package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.application.AdminReservationResult;

public record AdminReservationResponse(
	Long reservationId,
	Long memberId,
	String memberName,
	String memberPhone,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	String paymentSource,
	AdminReservationCouponResponse coupon,
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

	public static AdminReservationResponse from(AdminReservationResult result) {
		return new AdminReservationResponse(
			result.reservationId(),
			result.memberId(),
			result.memberName(),
			result.memberPhone(),
			result.classType(),
			result.lessonDate(),
			result.startTime(),
			result.status(),
			result.paymentSource(),
			result.coupon() == null ? null : AdminReservationCouponResponse.from(result.coupon()),
			result.paymentDueAt(),
			result.approvalRequestedAt(),
			result.adminConfirmedAt(),
			result.rejectedAt(),
			result.rejectedBy(),
			result.rejectionReason(),
			result.cancelledAt(),
			result.cancellationResponsibility(),
			result.couponAction(),
			result.adminMemo(),
			result.approvalWarning(),
			result.createdAt(),
			result.updatedAt());
	}
}
