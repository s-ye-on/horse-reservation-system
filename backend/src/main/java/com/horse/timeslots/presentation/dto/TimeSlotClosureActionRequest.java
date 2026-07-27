package com.horse.timeslots.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TimeSlotClosureActionRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String reason,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @PositiveOrZero Long expectedVersion
) {
}
