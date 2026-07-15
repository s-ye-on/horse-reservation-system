package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.application.MemberReservationResult;

public record MemberReservationResponse(
	Long reservationId,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	String paymentSource,
	MemberReservationCouponResponse coupon,
	LocalDateTime paymentDueAt,
	String rejectionReason,
	String couponAction,
	LocalDateTime approvalRequestedAt,
	LocalDateTime adminConfirmedAt,
	LocalDateTime rejectedAt,
	LocalDateTime cancelledAt
) {

	public static MemberReservationResponse from(MemberReservationResult result) {
		return new MemberReservationResponse(
			result.reservationId(),
			result.classType(),
			result.lessonDate(),
			result.startTime(),
			result.status(),
			result.paymentSource(),
			result.coupon() == null ? null : MemberReservationCouponResponse.from(result.coupon()),
			result.paymentDueAt(),
			result.rejectionReason(),
			result.couponAction(),
			result.approvalRequestedAt(),
			result.adminConfirmedAt(),
			result.rejectedAt(),
			result.cancelledAt());
	}
}
