package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.reservations.domain.Reservation;

public record ReservationRejectResult(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	LocalDateTime rejectedAt,
	String rejectedBy,
	String rejectionReason
) {

	public static ReservationRejectResult from(Reservation reservation) {
		return new ReservationRejectResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getCouponId(),
			reservation.getRejectedAt(),
			reservation.getRejectedBy(),
			reservation.getRejectionReason());
	}
}
