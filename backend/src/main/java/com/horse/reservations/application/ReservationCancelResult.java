package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.reservations.domain.CancellationResponsibility;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

public record ReservationCancelResult(
	Long reservationId,
	ReservationStatus status,
	CancellationResponsibility responsibility,
	CouponAction couponAction,
	LocalDateTime cancelledAt,
	boolean changed
) {

	public static ReservationCancelResult from(Reservation reservation, boolean changed) {
		return new ReservationCancelResult(
			reservation.getId(),
			reservation.getStatus(),
			reservation.getCancellationResponsibility(),
			reservation.getCouponAction(),
			reservation.getCancelledAt(),
			changed);
	}
}
