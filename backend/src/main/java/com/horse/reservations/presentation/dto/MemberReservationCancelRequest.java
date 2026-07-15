package com.horse.reservations.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberReservationCancelRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	String reason
) {
}
