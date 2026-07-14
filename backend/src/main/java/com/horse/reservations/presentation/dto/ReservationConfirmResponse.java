package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.ReservationConfirmResult;

public record ReservationConfirmResponse(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	LocalDateTime adminConfirmedAt
) {

	public static ReservationConfirmResponse from(ReservationConfirmResult result) {
		return new ReservationConfirmResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			result.adminConfirmedAt());
	}
}
