package com.horse.timeslots.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.application.MemberAvailableRidingClassesResult;
import com.horse.members.application.MemberAvailableRidingClassesService;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationBookingTimePolicy;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class MemberAvailableTimeSlotsService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final Clock clock;
	private final MemberAvailableRidingClassesService ridingClassesService;
	private final ScheduleDateRepository scheduleDateRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationRepository reservationRepository;

	public MemberAvailableTimeSlotsService(
		Clock clock,
		MemberAvailableRidingClassesService ridingClassesService,
		ScheduleDateRepository scheduleDateRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationRepository reservationRepository
	) {
		this.clock = clock;
		this.ridingClassesService = ridingClassesService;
		this.scheduleDateRepository = scheduleDateRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(readOnly = true)
	public MemberAvailableTimeSlotsResult getAvailableTimeSlots(
		String authSubject,
		String dateValue,
		String classTypeValue
	) {
		final LocalDate date = parseDate(dateValue);
		final RidingClass ridingClass = parseRidingClass(classTypeValue);
		final MemberAvailableRidingClassesResult member =
			ridingClassesService.getAvailableRidingClasses(authSubject);
		final boolean eligible = member.availableRidingClasses().contains(ridingClass);
		final boolean dateReservable = scheduleDateRepository.findByScheduleDate(date)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE))
			.isReservationInflowAllowed();
		if (!dateReservable) {
			return new MemberAvailableTimeSlotsResult(date, ridingClass, List.of());
		}
		final Instant requestedAt = clock.instant();
		final Map<java.time.LocalTime, List<Reservation>> reservationsByStartTime =
			reservationRepository.findOccupyingByLessonDate(
				date,
				ReservationStatus.occupyingStatuses()).stream()
				.collect(Collectors.groupingBy(Reservation::getStartTime));

		final List<MemberAvailableTimeSlotResult> timeSlots = timeSlotRepository
			.findAllByLessonDateOrderByStartTimeAsc(date).stream()
			.filter(timeSlot -> ReservationBookingTimePolicy.evaluate(
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				requestedAt).allowed())
			.map(timeSlot -> toResult(
				timeSlot,
				ridingClass,
				eligible,
				reservationsByStartTime.getOrDefault(timeSlot.getStartTime(), List.of())))
			.toList();
		return new MemberAvailableTimeSlotsResult(date, ridingClass, timeSlots);
	}

	private MemberAvailableTimeSlotResult toResult(
		TimeSlotCapacity timeSlot,
		RidingClass ridingClass,
		boolean eligible,
		List<Reservation> occupyingReservations
	) {
		final int totalRemaining = timeSlot.getTotalCapacity() - occupyingReservations.size();
		final int classRemaining = timeSlot.getClassCapacities().get(ridingClass.name())
			- (int)occupyingReservations.stream()
				.filter(reservation -> reservation.getRidingClass() == ridingClass)
				.count();
		final int roundRemaining = timeSlot.getRoundArenaCapacity()
			- (int)occupyingReservations.stream()
				.filter(reservation -> TimeSlotCapacity.usesRoundArena(reservation.getRidingClass()))
				.count();
		final int remainingCapacity = TimeSlotCapacity.usesRoundArena(ridingClass)
			? Math.min(totalRemaining, Math.min(roundRemaining, classRemaining))
			: Math.min(totalRemaining, classRemaining);
		final TimeSlotAvailabilityReason unavailableReason = availabilityReason(
			eligible,
			timeSlot.isClosed(),
			remainingCapacity);
		return new MemberAvailableTimeSlotResult(
			timeSlot.getId(),
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			timeSlot.isClosed(),
			unavailableReason == null,
			Math.max(remainingCapacity, 0),
			unavailableReason);
	}

	private TimeSlotAvailabilityReason availabilityReason(
		boolean eligible,
		boolean closed,
		int remainingCapacity
	) {
		if (!eligible) {
			return TimeSlotAvailabilityReason.NOT_ELIGIBLE;
		}
		if (closed) {
			return TimeSlotAvailabilityReason.CLOSED;
		}
		if (remainingCapacity <= 0) {
			return TimeSlotAvailabilityReason.FULL;
		}
		return null;
	}

	private LocalDate parseDate(String dateValue) {
		try {
			final LocalDate date = LocalDate.parse(dateValue);
			if (date.isBefore(LocalDate.ofInstant(clock.instant(), SEOUL_ZONE))) {
				throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_QUERY_DATE);
			}
			return date;
		}
		catch (DateTimeParseException | NullPointerException exception) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_QUERY_DATE);
		}
	}

	private RidingClass parseRidingClass(String classTypeValue) {
		return Arrays.stream(RidingClass.values())
			.filter(ridingClass -> ridingClass.name().equals(classTypeValue))
			.findFirst()
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLASS_TYPE));
	}
}
