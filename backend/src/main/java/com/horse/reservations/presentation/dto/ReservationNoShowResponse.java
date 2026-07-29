package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationNoShowResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationNoShowResponse(
	Long reservationId,
	String status,
	String paymentSource,
	@Schema(nullable = true) Long couponId,
	String couponAction,
	@Schema(nullable = true) String adminMemo
) {

	public static ReservationNoShowResponse from(ReservationNoShowResult result) {
		return new ReservationNoShowResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			result.couponAction(),
			result.adminMemo());
	}
}
