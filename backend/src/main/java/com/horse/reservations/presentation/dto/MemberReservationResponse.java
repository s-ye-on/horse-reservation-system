package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.MemberReservationResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberReservationResponse(
	Long reservationId,
	String classType,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	String status,
	String paymentSource,
	@Schema(nullable = true) MemberReservationCouponResponse coupon,
	@Schema(nullable = true) OffsetDateTime paymentDueAt,
	@Schema(nullable = true) String rejectionReason,
	@Schema(nullable = true) String couponAction,
	OffsetDateTime approvalRequestedAt,
	@Schema(nullable = true) OffsetDateTime adminConfirmedAt,
	@Schema(nullable = true) OffsetDateTime rejectedAt,
	@Schema(nullable = true) OffsetDateTime cancelledAt,
	String displayGroup,
	ReservationActionsResponse actions
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
			ApiDateTime.toSeoulOffset(result.paymentDueAt()),
			result.rejectionReason(),
			result.couponAction(),
			ApiDateTime.toSeoulOffset(result.approvalRequestedAt()),
			ApiDateTime.toSeoulOffset(result.adminConfirmedAt()),
			ApiDateTime.toSeoulOffset(result.rejectedAt()),
			ApiDateTime.toSeoulOffset(result.cancelledAt()),
			result.displayGroup(),
			ReservationActionsResponse.from(result.actions()));
	}
}
