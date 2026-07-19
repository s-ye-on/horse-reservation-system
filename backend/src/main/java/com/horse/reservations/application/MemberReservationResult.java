package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.coupons.domain.Coupon;
import com.horse.reservations.domain.Reservation;

public record MemberReservationResult(
	Long reservationId,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	String paymentSource,
	MemberReservationCouponResult coupon,
	LocalDateTime paymentDueAt,
	String rejectionReason,
	String couponAction,
	LocalDateTime approvalRequestedAt,
	LocalDateTime adminConfirmedAt,
	LocalDateTime rejectedAt,
	LocalDateTime cancelledAt,
	String displayGroup,
	ReservationActionsResult actions
) {

	public static MemberReservationResult from(
		Reservation reservation,
		Coupon coupon,
		LocalDateTime actionAt
	) {
		return new MemberReservationResult(
			reservation.getId(),
			reservation.getRidingClass().name(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			coupon == null ? null : MemberReservationCouponResult.from(coupon),
			reservation.getPaymentDueAt(),
			reservation.getRejectionReason(),
			reservation.getCouponAction() == null ? null : reservation.getCouponAction().databaseValue(),
			reservation.getApprovalRequestedAt(),
			reservation.getAdminConfirmedAt(),
			reservation.getRejectedAt(),
			reservation.getCancelledAt(),
			reservation.displayGroupAt(actionAt).name(),
			ReservationActionsResult.from(reservation, actionAt));
	}
}
