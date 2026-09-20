package com.horse.schedules.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.ScheduleDateStatus;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotSource;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ScheduleOccurrenceDateSynchronizer {
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final Clock clock;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateRepository scheduleDateRepository;
	private final RegularScheduleTemplateRepository templateRepository;
	private final RecurringHolidayRuleRepository holidayRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationRepository reservationRepository;

	public ScheduleOccurrenceDateSynchronizer(
		Clock clock,
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateRepository scheduleDateRepository,
		RegularScheduleTemplateRepository templateRepository,
		RecurringHolidayRuleRepository holidayRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationRepository reservationRepository
	) {
		this.clock = clock;
		this.configGuardRepository = configGuardRepository;
		this.scheduleDateRepository = scheduleDateRepository;
		this.templateRepository = templateRepository;
		this.holidayRepository = holidayRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ScheduleOccurrenceDateResult synchronize(
		long expectedPendingVersion,
		LocalDate targetDate
	) {
		return synchronize(expectedPendingVersion, targetDate, true);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ScheduleOccurrenceDateResult synchronizeActive(
		long expectedActiveVersion,
		LocalDate targetDate
	) {
		return synchronize(expectedActiveVersion, targetDate, false);
	}

	private ScheduleOccurrenceDateResult synchronize(
		long expectedConfigVersion,
		LocalDate targetDate,
		boolean pendingSynchronization
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		if (pendingSynchronization && guard.getActiveVersion() == expectedConfigVersion) {
			return ScheduleOccurrenceDateResult.skipped(targetDate);
		}
		if (pendingSynchronization) {
			guard.ensurePendingVersion(expectedConfigVersion);
		}
		else {
			guard.ensureActiveVersion(expectedConfigVersion);
		}
		final ScheduleDate scheduleDate = scheduleDateRepository.findByScheduleDateForUpdate(targetDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
		if (pendingSynchronization
			&& scheduleDate.getAppliedConfigVersion() == expectedConfigVersion) {
			return ScheduleOccurrenceDateResult.skipped(targetDate);
		}
		final boolean closedDate = scheduleDate.getStatus() == ScheduleDateStatus.CLOSING
			|| scheduleDate.getStatus() == ScheduleDateStatus.CLOSED;

		final List<RegularScheduleTemplate> templates =
			templateRepository.findAllByDayOfWeekOrderByStartTimeAscIdAsc(
				targetDate.getDayOfWeek());
		final Map<LocalTime, RegularScheduleTemplate> activeTemplates =
			activeTemplatesByStartTime(templates);
		final Map<LocalTime, RegularScheduleTemplate> capacityTemplates =
			capacityTemplatesByStartTime(templates);
		final boolean recurringHoliday = !closedDate
			&& scheduleDate.getStatus() != ScheduleDateStatus.OPEN
			&& holidayRepository.existsActiveOnDate(
				targetDate.getDayOfWeek(),
				targetDate);
		final List<TimeSlotCapacity> existingTimeSlots =
			timeSlotRepository.findAllByLessonDateForUpdateOrdered(targetDate);
		final Map<LocalTime, TimeSlotCapacity> existingByStartTime =
			timeSlotsByStartTime(existingTimeSlots);
		final int updatedCount = synchronizeExisting(
			existingTimeSlots,
			activeTemplates,
			capacityTemplates,
			recurringHoliday,
			closedDate,
			pendingSynchronization,
			occupancyForDate(targetDate, pendingSynchronization));
		final int createdCount = recurringHoliday || closedDate
			? 0
			: createMissing(
				targetDate,
				activeTemplates,
				existingByStartTime);
		final boolean applied = scheduleDate.applyConfigVersion(expectedConfigVersion);
		timeSlotRepository.flush();
		return new ScheduleOccurrenceDateResult(
			targetDate,
			createdCount,
			updatedCount,
			applied,
			false);
	}

	private Map<LocalTime, RegularScheduleTemplate> activeTemplatesByStartTime(
		List<RegularScheduleTemplate> templates
	) {
		final Map<LocalTime, RegularScheduleTemplate> activeTemplates = new LinkedHashMap<>();
		for (RegularScheduleTemplate template : templates) {
			if (template.isActive()) {
				activeTemplates.put(template.getStartTime(), template);
			}
		}
		return Map.copyOf(activeTemplates);
	}

	private Map<LocalTime, RegularScheduleTemplate> capacityTemplatesByStartTime(
		List<RegularScheduleTemplate> templates
	) {
		final Map<LocalTime, RegularScheduleTemplate> byStartTime = new LinkedHashMap<>();
		for (RegularScheduleTemplate template : templates) {
			byStartTime.put(template.getStartTime(), template);
		}
		return Map.copyOf(byStartTime);
	}

	private Map<LocalTime, TimeSlotCapacity> timeSlotsByStartTime(
		List<TimeSlotCapacity> timeSlots
	) {
		final Map<LocalTime, TimeSlotCapacity> byStartTime = new LinkedHashMap<>();
		for (TimeSlotCapacity timeSlot : timeSlots) {
			byStartTime.put(timeSlot.getStartTime(), timeSlot);
		}
		return Map.copyOf(byStartTime);
	}

	private int synchronizeExisting(
		List<TimeSlotCapacity> existingTimeSlots,
		Map<LocalTime, RegularScheduleTemplate> activeTemplates,
		Map<LocalTime, RegularScheduleTemplate> capacityTemplates,
		boolean recurringHoliday,
		boolean closedDate,
		boolean pendingSynchronization,
		Map<LocalTime, Occupancy> occupancy
	) {
		int updatedCount = 0;
		final LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
		for (TimeSlotCapacity timeSlot : existingTimeSlots) {
			if (timeSlot.getSource() != TimeSlotSource.TEMPLATE
				|| timeSlot.getLessonDate().atTime(timeSlot.getStartTime()).isBefore(now)
				|| timeSlot.getLessonDate().atTime(timeSlot.getStartTime()).equals(now)) {
				continue;
			}
			final RegularScheduleTemplate desiredTemplate =
				activeTemplates.get(timeSlot.getStartTime());
			final RegularScheduleTemplate capacityTemplate =
				capacityTemplates.get(timeSlot.getStartTime());
			final Long templateId = desiredTemplate == null
				? timeSlot.getTemplateId()
				: desiredTemplate.getId();
			final boolean templateInactive = desiredTemplate == null;
			final boolean desiredHolidayClosure = closedDate
				? timeSlot.isRecurringHolidayClosed() : recurringHoliday;
			boolean changed = timeSlot.isRecurringHolidayClosed() != desiredHolidayClosure
				|| timeSlot.isTemplateInactiveClosed() != templateInactive
				|| !timeSlot.getTemplateId().equals(templateId);
			timeSlot.synchronizeTemplateOccurrence(
				templateId, desiredHolidayClosure, templateInactive);
			if (pendingSynchronization && capacityTemplate != null
				&& (capacityTemplate.isActive()
					|| capacityTemplate.getId().equals(timeSlot.getTemplateId()))
				&& !timeSlot.isCapacityOverridden()) {
				final Occupancy occupied = occupancy.getOrDefault(
					timeSlot.getStartTime(), new Occupancy());
				changed = timeSlot.synchronizeTemplateCapacity(
					capacityTemplate.getTotalCapacity(),
					capacityTemplate.getRoundArenaCapacity(),
					capacityTemplate.getClassCapacities(),
					occupied.total,
					occupied.round,
					occupied.byClass) || changed;
			}
			if (changed) {
				updatedCount++;
			}
		}
		return updatedCount;
	}

	private Map<LocalTime, Occupancy> occupancyForDate(
		LocalDate date,
		boolean pendingSynchronization
	) {
		if (!pendingSynchronization) {
			return Map.of();
		}
		final Map<LocalTime, Occupancy> occupancy = new HashMap<>();
		for (Reservation reservation : reservationRepository.findOccupyingByLessonDate(
			date, ReservationStatus.occupyingStatuses())) {
			occupancy.computeIfAbsent(reservation.getStartTime(), ignored -> new Occupancy())
				.add(reservation.getRidingClass());
		}
		return occupancy;
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

	private int createMissing(
		LocalDate targetDate,
		Map<LocalTime, RegularScheduleTemplate> activeTemplates,
		Map<LocalTime, TimeSlotCapacity> existingByStartTime
	) {
		int createdCount = 0;
		for (RegularScheduleTemplate template : activeTemplates.values()) {
			if (existingByStartTime.containsKey(template.getStartTime())) {
				continue;
			}
			timeSlotRepository.save(TimeSlotCapacity.createFromTemplate(
				targetDate,
				template.getId(),
				template.getStartTime(),
				template.getEndTime(),
				template.getTotalCapacity(),
				template.getRoundArenaCapacity(),
				template.getClassCapacities(),
				false));
			createdCount++;
		}
		return createdCount;
	}
}
