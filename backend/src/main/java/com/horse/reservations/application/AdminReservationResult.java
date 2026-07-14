package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.coupons.domain.Coupon;
import com.horse.members.domain.Member;
import com.horse.reservations.domain.Reservation;

public record AdminReservationResult(
	Long reservationId,
	Long memberId,
	String memberName,
	String memberPhone,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	String paymentSource,
	AdminReservationCouponResult coupon,
	LocalDateTime paymentDueAt,
	LocalDateTime approvalRequestedAt,
	LocalDateTime adminConfirmedAt,
	LocalDateTime rejectedAt,
	String rejectedBy,
	String rejectionReason,
	LocalDateTime cancelledAt,
	String cancellationResponsibility,
	String couponAction,
	String adminMemo,
	String approvalWarning,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static AdminReservationResult from(
		Reservation reservation,
		Member member,
		Coupon coupon,
		ReservationApprovalWarningLevel approvalWarning
	) {
		return new AdminReservationResult(
			reservation.getId(),
			member.getId(),
			member.getName(),
			member.getPhone(),
			reservation.getRidingClass().name(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus().databaseValue(),
			reservation.getPaymentSource().databaseValue(),
			coupon == null ? null : AdminReservationCouponResult.from(coupon),
			reservation.getPaymentDueAt(),
			reservation.getApprovalRequestedAt(),
			reservation.getAdminConfirmedAt(),
			reservation.getRejectedAt(),
			reservation.getRejectedBy(),
			reservation.getRejectionReason(),
			reservation.getCancelledAt(),
			reservation.getCancellationResponsibility() == null
				? null : reservation.getCancellationResponsibility().databaseValue(),
			reservation.getCouponAction() == null ? null : reservation.getCouponAction().databaseValue(),
			reservation.getAdminMemo(),
			approvalWarning == null ? null : approvalWarning.apiValue(),
			reservation.getCreatedAt(),
			reservation.getUpdatedAt());
	}
}
