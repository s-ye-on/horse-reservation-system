package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationNoShowResult;

public record ReservationNoShowResponse(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	String couponAction,
	String adminMemo
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
