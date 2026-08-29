package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.ReservationStatus;

public interface WeeklyOperationsReservationProjection {

	Long getReservationId();

	LocalDate getLessonDate();

	LocalTime getStartTime();

	Long getMemberId();

	String getMemberName();

	RidingClass getRidingClass();

	ReservationStatus getStatus();

}
