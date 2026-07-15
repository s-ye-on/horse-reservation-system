package com.horse.timeslots.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.timeslots.application.TimeSlotReservationHistoryQuery;

@Component
public class ReservationTimeSlotHistoryQuery implements TimeSlotReservationHistoryQuery {

	private final ReservationRepository reservationRepository;

	public ReservationTimeSlotHistoryQuery(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@Override
	public boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime) {
		return reservationRepository.existsByLessonDateAndStartTime(lessonDate, startTime);
	}

	@Override
	public List<RidingClass> findActiveRidingClassesForUpdate(LocalDate lessonDate, LocalTime startTime) {
		return reservationRepository.findOccupyingByLessonDateAndStartTimeForUpdate(
			lessonDate,
			startTime,
			ReservationStatus.occupyingStatuses()).stream()
			.map(Reservation::getRidingClass)
			.toList();
	}
}
