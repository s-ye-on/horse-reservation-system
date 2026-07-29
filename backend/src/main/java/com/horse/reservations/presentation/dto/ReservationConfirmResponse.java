package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationConfirmResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationConfirmResponse(
	Long reservationId,
	String status,
	String paymentSource,
	@Schema(nullable = true) Long couponId,
	OffsetDateTime adminConfirmedAt
) {

	public static ReservationConfirmResponse from(ReservationConfirmResult result) {
		return new ReservationConfirmResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			ApiDateTime.toSeoulOffset(result.adminConfirmedAt()));
	}
}
