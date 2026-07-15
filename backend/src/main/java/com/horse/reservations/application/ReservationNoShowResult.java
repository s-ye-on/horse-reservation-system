package com.horse.reservations.application;

import com.horse.reservations.domain.Reservation;

public record ReservationNoShowResult(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	String couponAction,
	String adminMemo
) {

	public static ReservationNoShowResult from(Reservation reservation) {
		return new ReservationNoShowResult(
			reservation.getId(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			reservation.getCouponId(),
			reservation.getCouponAction().databaseValue(),
			reservation.getAdminMemo());
	}
}
