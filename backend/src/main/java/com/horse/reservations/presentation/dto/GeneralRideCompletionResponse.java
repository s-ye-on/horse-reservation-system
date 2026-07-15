package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.GeneralRideCompletionResult;

public record GeneralRideCompletionResponse(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	int generalRideCount
) {

	public static GeneralRideCompletionResponse from(GeneralRideCompletionResult result) {
		return new GeneralRideCompletionResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			result.generalRideCount());
	}
}
