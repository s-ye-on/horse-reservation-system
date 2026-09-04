package com.horse.coupons.presentation.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CouponRegistrationRequest(
	@NotBlank String type,
	@NotNull @Positive @Schema(minimum = "1") Integer totalCount,
	@NotNull @PositiveOrZero @Schema(minimum = "0") Integer usedCount,
	@Schema(nullable = true) LocalDate firstUsedDate
) {
}
