package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberReservationQueryRequest(
	@Schema(allowableValues = {"UPCOMING", "PAST"})
	String displayGroup,
	@Schema(allowableValues = {
		"pending_admin_approval",
		"pending_payment",
		"payment_expired",
		"approval_expired",
		"confirmed",
		"completed",
		"rejected",
		"cancelled",
		"no_show"
	})
	String status,
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
