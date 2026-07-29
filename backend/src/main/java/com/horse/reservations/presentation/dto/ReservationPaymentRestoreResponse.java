package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationPaymentRestoreResult;

public record ReservationPaymentRestoreResponse(
	Long reservationId,
	String status,
	String paymentSource,
	OffsetDateTime adminConfirmedAt
) {

	public static ReservationPaymentRestoreResponse from(ReservationPaymentRestoreResult result) {
		return new ReservationPaymentRestoreResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			ApiDateTime.toSeoulOffset(result.adminConfirmedAt()));
	}
}
