package com.horse.schedules.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotSource;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ScheduleTemplateCapacityPreflight {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;
	private static final String RECONCILIATION_ACTOR = "system:schedule-capacity-reconciliation";

	private final Clock clock;
	private final ScheduleConfigGuardRepository guardRepository;
	private final ScheduleDateRepository dateRepository;
	private final RegularScheduleTemplateRepository templateRepository;
	private final TimeSlotCapacityRepository slotRepository;
	private final ReservationRepository reservationRepository;

	public ScheduleTemplateCapacityPreflight(
		Clock clock,
		ScheduleConfigGuardRepository guardRepository,
		ScheduleDateRepository dateRepository,
		RegularScheduleTemplateRepository templateRepository,
		TimeSlotCapacityRepository slotRepository,
		ReservationRepository reservationRepository
	) {
		this.clock = clock;
		this.guardRepository = guardRepository;
		this.dateRepository = dateRepository;
		this.templateRepository = templateRepository;
		this.slotRepository = slotRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void validateBeforeMutation() {
		validateLockedFutureSlots();
	}

	@Transactional
	public void validatePendingSynchronization(long expectedPendingVersion) {
		guardRepository.findSingletonForShare().ensurePendingVersion(expectedPendingVersion);
		validateLockedFutureSlots();
	}

	@Transactional
	public Long beginReconciliationIfNeeded() {
		final ScheduleConfigGuard guard = guardRepository.findSingletonForUpdate();
		if (guard.getPendingVersion() != null) {
			return guard.getPendingVersion();
		}
		final LocalDateTime now = now();
		if (!hasCapacityMismatch(futureSlots(now), templatesByKey())) {
			return null;
		}
		if (!validateLockedFutureSlots()) {
			return null;
		}
		return guard.beginSynchronization(
			guard.getActiveVersion(), now, RECONCILIATION_ACTOR);
	}

	private boolean validateLockedFutureSlots() {
		final LocalDateTime now = now();
		final LocalDate end = now.toLocalDate().plusMonths(HORIZON_MONTHS);
		// Keep the established guard -> dates -> slots -> reservations lock order.
		dateRepository.findAllByScheduleDateBetweenForUpdate(now.toLocalDate(), end);
		final List<TimeSlotCapacity> candidates = futureSlots(now);
		if (candidates.isEmpty()) {
			return false;
		}
		final List<Long> ids = candidates.stream().map(TimeSlotCapacity::getId).toList();
		final List<TimeSlotCapacity> locked = slotRepository.findAllByIdForUpdateOrdered(ids);
		final Map<TemplateKey, RegularScheduleTemplate> templates = templatesByKey();
		final Map<SlotKey, Occupancy> occupancy = occupancy(now.toLocalDate(), end);
		return validate(locked, templates, occupancy, now);
	}

	private boolean hasCapacityMismatch(
		List<TimeSlotCapacity> slots,
		Map<TemplateKey, RegularScheduleTemplate> templates
	) {
		for (TimeSlotCapacity slot : slots) {
			final RegularScheduleTemplate template = templates.get(templateKey(slot));
			if (appliesToSlot(slot, template) && differs(slot, template)) {
				return true;
			}
		}
		return false;
	}

	private boolean validate(
		List<TimeSlotCapacity> slots,
		Map<TemplateKey, RegularScheduleTemplate> templates,
		Map<SlotKey, Occupancy> occupancy,
		LocalDateTime now
	) {
		boolean mismatch = false;
		for (TimeSlotCapacity slot : slots) {
			if (!isFuture(slot, now)) {
				continue;
			}
			final RegularScheduleTemplate template = templates.get(templateKey(slot));
			if (!appliesToSlot(slot, template) || !differs(slot, template)) {
				continue;
			}
			mismatch = true;
			final Occupancy occupied = occupancy.getOrDefault(
				new SlotKey(slot.getLessonDate(), slot.getStartTime()), new Occupancy());
			try {
				slot.ensureCanSynchronizeTemplateCapacity(
					template.getTotalCapacity(),
					template.getRoundArenaCapacity(),
					template.getClassCapacities(),
					occupied.total,
					occupied.round,
					occupied.byClass);
			}
			catch (TimeSlotException exception) {
				if (!ExceptionCode.TIMESLOT_CAPACITY_BELOW_OCCUPANCY.code().equals(exception.code())) {
					throw exception;
				}
				throw new TimeSlotException(ExceptionCode.TIMESLOT_CAPACITY_BELOW_OCCUPANCY, Map.of(
					"lessonDate", slot.getLessonDate().toString(),
					"startTime", slot.getStartTime().toString(),
					"totalOccupied", occupied.total,
					"roundArenaOccupied", occupied.round,
					"classOccupied", occupied.byClass,
					"requestedTotalCapacity", template.getTotalCapacity(),
					"requestedRoundArenaCapacity", template.getRoundArenaCapacity(),
					"requestedClassCapacities", template.getClassCapacities()));
			}
		}
		return mismatch;
	}

	private Map<SlotKey, Occupancy> occupancy(LocalDate from, LocalDate to) {
		final Map<SlotKey, Occupancy> bySlot = new HashMap<>();
		for (Reservation reservation : reservationRepository.findOccupyingBetweenForUpdate(
			from, to, ReservationStatus.occupyingStatuses())) {
			bySlot.computeIfAbsent(
				new SlotKey(reservation.getLessonDate(), reservation.getStartTime()),
				ignored -> new Occupancy()).add(reservation.getRidingClass());
		}
		return bySlot;
	}

	private List<TimeSlotCapacity> futureSlots(LocalDateTime now) {
		final List<TimeSlotCapacity> slots = slotRepository.findInheritedTemplateSlotsBetween(
			now.toLocalDate(), now.toLocalDate().plusMonths(HORIZON_MONTHS),
			TimeSlotSource.TEMPLATE);
		final List<TimeSlotCapacity> future = new ArrayList<>();
		for (TimeSlotCapacity slot : slots) {
			if (isFuture(slot, now)) {
				future.add(slot);
			}
		}
		return future;
	}

	private boolean isFuture(TimeSlotCapacity slot, LocalDateTime now) {
		return slot.getLessonDate().isAfter(now.toLocalDate())
			|| (slot.getLessonDate().equals(now.toLocalDate())
				&& slot.getStartTime().isAfter(now.toLocalTime()));
	}

	private Map<TemplateKey, RegularScheduleTemplate> templatesByKey() {
		final Map<TemplateKey, RegularScheduleTemplate> templates = new HashMap<>();
		for (RegularScheduleTemplate template : templateRepository.findAll()) {
			templates.put(new TemplateKey(template.getDayOfWeek(), template.getStartTime()), template);
		}
		return templates;
	}

	private boolean appliesToSlot(
		TimeSlotCapacity slot,
		RegularScheduleTemplate template
	) {
		return template != null
			&& (template.isActive() || template.getId().equals(slot.getTemplateId()));
	}

	private TemplateKey templateKey(TimeSlotCapacity slot) {
		return new TemplateKey(slot.getLessonDate().getDayOfWeek(), slot.getStartTime());
	}

	private boolean differs(TimeSlotCapacity slot, RegularScheduleTemplate template) {
		return slot.getTotalCapacity() != template.getTotalCapacity()
			|| slot.getRoundArenaCapacity() != template.getRoundArenaCapacity()
			|| !slot.getClassCapacities().equals(template.getClassCapacities());
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
	}

	private record TemplateKey(DayOfWeek dayOfWeek, LocalTime startTime) {
	}

	private record SlotKey(LocalDate date, LocalTime startTime) {
	}

	private static final class Occupancy {
		private int total;
		private int round;
		private final Map<RidingClass, Integer> byClass = new EnumMap<>(RidingClass.class);

		private void add(RidingClass ridingClass) {
			total++;
			if (TimeSlotCapacity.usesRoundArena(ridingClass)) {
				round++;
			}
			byClass.merge(ridingClass, 1, Integer::sum);
		}
	}
}
