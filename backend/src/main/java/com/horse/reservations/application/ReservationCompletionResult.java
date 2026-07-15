package com.horse.reservations.application;

import com.horse.members.domain.Member;
import com.horse.reservations.domain.Reservation;

public record ReservationCompletionResult(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	int generalRideCount,
	int dressageRideCount,
	int jumpingRideCount
) {

	public static ReservationCompletionResult from(Reservation reservation, Member member) {
		return new ReservationCompletionResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getCouponId(),
			member.getGeneralRideCount(),
			member.getDressageRideCount(),
			member.getJumpingRideCount());
	}
}
