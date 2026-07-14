package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.reservations.domain.Reservation;

public record ReservationConfirmResult(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	LocalDateTime adminConfirmedAt
) {

	public static ReservationConfirmResult from(Reservation reservation) {
		return new ReservationConfirmResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getCouponId(),
			reservation.getAdminConfirmedAt());
	}
}
