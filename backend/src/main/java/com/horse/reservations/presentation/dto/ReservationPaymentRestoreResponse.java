package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.ReservationPaymentRestoreResult;

public record ReservationPaymentRestoreResponse(
	Long reservationId,
	String status,
	String paymentSource,
	LocalDateTime adminConfirmedAt
) {

	public static ReservationPaymentRestoreResponse from(ReservationPaymentRestoreResult result) {
		return new ReservationPaymentRestoreResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.adminConfirmedAt());
	}
}
