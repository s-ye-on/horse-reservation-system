package com.horse.reservations.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotSource;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class AdminWeeklyOperationsCalendarService {

	private final Clock clock;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationRepository reservationRepository;

	public AdminWeeklyOperationsCalendarService(
		Clock clock,
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationRepository reservationRepository
	) {
		this.clock = clock;
		this.timeSlotRepository = timeSlotRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(readOnly = true)
	public AdminWeeklyOperationsCalendarResult getCalendar(LocalDate referenceDate) {
		final LocalDateTime requestedAt = LocalDateTime.now(clock);
		final LocalDate resolvedReferenceDate = referenceDate == null ? requestedAt.toLocalDate() : referenceDate;
		final LocalDate weekStartDate = resolvedReferenceDate.with(
			TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		final LocalDate weekEndDate = weekStartDate.plusDays(6);
		final List<TimeSlotCapacity> timeSlots = timeSlotRepository
			.findOperationsCalendarVisibleBetween(
				weekStartDate,
				weekEndDate,
				requestedAt.toLocalDate(),
				requestedAt.toLocalTime(),
				TimeSlotSource.TEMPLATE);
		final Map<TimeSlotKey, List<AdminWeeklyOperationsReservationResult>> reservationsByTimeSlot =
			reservationRepository.findWeeklyOperationsReservations(
				weekStartDate,
				weekEndDate,
				calendarVisibleStatuses()).stream()
			.collect(Collectors.groupingBy(
				projection -> new TimeSlotKey(projection.getLessonDate(), projection.getStartTime()),
				LinkedHashMap::new,
				Collectors.mapping(AdminWeeklyOperationsReservationResult::from, Collectors.toList())));
		final List<AdminWeeklyOperationsTimeSlotResult> results = timeSlots.stream()
			.map(timeSlot -> AdminWeeklyOperationsTimeSlotResult.from(
				timeSlot,
				reservationsByTimeSlot.getOrDefault(
					new TimeSlotKey(timeSlot.getLessonDate(), timeSlot.getStartTime()),
					List.of())))
			.toList();

		return new AdminWeeklyOperationsCalendarResult(
			resolvedReferenceDate,
			weekStartDate,
			weekEndDate,
			results);
	}

	private Set<ReservationStatus> calendarVisibleStatuses() {
		final EnumSet<ReservationStatus> statuses = EnumSet.copyOf(ReservationStatus.occupyingStatuses());
		statuses.add(ReservationStatus.COMPLETED);
		return statuses;
	}

	private record TimeSlotKey(LocalDate lessonDate, LocalTime startTime) {
	}

}
