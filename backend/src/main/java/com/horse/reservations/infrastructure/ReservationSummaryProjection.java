package com.horse.reservations.infrastructure;

import java.time.LocalDate;

import com.horse.reservations.domain.ReservationStatus;

public interface ReservationSummaryProjection {

	LocalDate getLessonDate();

	ReservationStatus getStatus();

	long getReservationCount();
}
