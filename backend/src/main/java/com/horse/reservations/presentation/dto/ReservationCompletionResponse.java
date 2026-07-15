package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationCompletionResult;

public record ReservationCompletionResponse(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	int generalRideCount,
	int dressageRideCount,
	int jumpingRideCount
) {

	public static ReservationCompletionResponse from(ReservationCompletionResult result) {
		return new ReservationCompletionResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			result.generalRideCount(),
			result.dressageRideCount(),
			result.jumpingRideCount());
	}
}
