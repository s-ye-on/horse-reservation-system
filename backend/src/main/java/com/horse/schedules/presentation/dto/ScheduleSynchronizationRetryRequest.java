package com.horse.schedules.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScheduleSynchronizationRetryRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive Long pendingVersion
) {
}
