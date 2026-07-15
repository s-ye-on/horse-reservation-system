package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

public record ReservationChangeResult(
	Long reservationId,
	LocalDate lessonDate,
	LocalTime startTime,
	ReservationStatus status,
	CouponAction couponAction,
	boolean freeChangeUsed,
	boolean changed
) {

	public static ReservationChangeResult withoutCouponAction(Reservation reservation, boolean changed) {
		return new ReservationChangeResult(
			reservation.getId(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus(),
			CouponAction.NONE,
			false,
			changed);
	}

	public static ReservationChangeResult freeChangeUsed(Reservation reservation, boolean changed) {
		return new ReservationChangeResult(
			reservation.getId(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus(),
			CouponAction.FREE_CHANGE_USED,
			true,
			changed);
	}
}
