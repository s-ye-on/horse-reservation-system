package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.reservations.domain.Reservation;

public record ReservationPaymentRestoreResult(
	Long reservationId,
	String status,
	String paymentSource,
	LocalDateTime adminConfirmedAt
) {

	public static ReservationPaymentRestoreResult from(Reservation reservation) {
		return new ReservationPaymentRestoreResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getAdminConfirmedAt());
	}
}
