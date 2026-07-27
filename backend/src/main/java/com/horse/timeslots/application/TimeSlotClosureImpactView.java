package com.horse.timeslots.application;

import com.horse.reservations.application.AdminReservationResult;
import com.horse.reservations.domain.ReservationStatus;

public record TimeSlotClosureImpactView(
	long reservationId,
	String reservationStatusAtStart,
	String currentStatus,
	boolean resolved,
	boolean moved,
	long memberId,
	String memberName,
	String memberPhone,
	String classType,
	String paymentSource,
	Long couponId
) {

	public static TimeSlotClosureImpactView from(
		ReservationStatus statusAtStart,
		AdminReservationResult reservation,
		boolean resolved,
		boolean moved
	) {
		return new TimeSlotClosureImpactView(
			reservation.reservationId(),
			statusAtStart.databaseValue(),
			reservation.status(),
			resolved,
			moved,
			reservation.memberId(),
			reservation.memberName(),
			reservation.memberPhone(),
			reservation.classType(),
			reservation.paymentSource(),
			reservation.coupon() == null ? null : reservation.coupon().couponId());
	}
}
