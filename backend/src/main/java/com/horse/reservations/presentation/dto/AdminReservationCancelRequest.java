package com.horse.reservations.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminReservationCancelRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String responsibility,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	String couponAction,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	String memo
) {
}
