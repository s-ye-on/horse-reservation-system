package com.horse.reservations.application;

import com.horse.reservations.domain.Reservation;

public record GeneralRideCompletionResult(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	int generalRideCount
) {

	public static GeneralRideCompletionResult from(Reservation reservation, int generalRideCount) {
		return new GeneralRideCompletionResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getCouponId(),
			generalRideCount);
	}
}
