package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.AdminReservationResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminReservationResponse(
	Long reservationId,
	Long memberId,
	String memberName,
	String memberPhone,
	String classType,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	String status,
	String paymentSource,
	@Schema(nullable = true) AdminReservationCouponResponse coupon,
	@Schema(nullable = true) OffsetDateTime paymentDueAt,
	OffsetDateTime approvalRequestedAt,
	@Schema(nullable = true) OffsetDateTime adminConfirmedAt,
	@Schema(nullable = true) OffsetDateTime rejectedAt,
	@Schema(nullable = true) String rejectedBy,
	@Schema(nullable = true) String rejectionReason,
	@Schema(nullable = true) OffsetDateTime cancelledAt,
	@Schema(nullable = true) String cancellationResponsibility,
	@Schema(nullable = true) String couponAction,
	@Schema(nullable = true) String adminMemo,
	@Schema(nullable = true) String approvalWarning,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	String displayGroup,
	ReservationActionsResponse actions
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
			ApiDateTime.toSeoulOffset(result.paymentDueAt()),
			ApiDateTime.toSeoulOffset(result.approvalRequestedAt()),
			ApiDateTime.toSeoulOffset(result.adminConfirmedAt()),
			ApiDateTime.toSeoulOffset(result.rejectedAt()),
			result.rejectedBy(),
			result.rejectionReason(),
			ApiDateTime.toSeoulOffset(result.cancelledAt()),
			result.cancellationResponsibility(),
			result.couponAction(),
			result.adminMemo(),
			result.approvalWarning(),
			ApiDateTime.toSeoulOffset(result.createdAt()),
			ApiDateTime.toSeoulOffset(result.updatedAt()),
			result.displayGroup(),
			ReservationActionsResponse.from(result.actions()));
	}
}
