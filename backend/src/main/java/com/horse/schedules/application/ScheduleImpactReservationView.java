package com.horse.schedules.application;

import com.horse.reservations.application.AdminReservationResult;

public record ScheduleImpactReservationView(
	long reservationId,
	long memberId,
	String memberName,
	String memberPhone,
	String classType,
	String status,
	String paymentSource,
	Long couponId
) {

	public static ScheduleImpactReservationView from(AdminReservationResult reservation) {
		return new ScheduleImpactReservationView(
			reservation.reservationId(),
			reservation.memberId(),
			reservation.memberName(),
			reservation.memberPhone(),
			reservation.classType(),
			reservation.status(),
			reservation.paymentSource(),
			reservation.coupon() == null ? null : reservation.coupon().couponId());
	}
}
