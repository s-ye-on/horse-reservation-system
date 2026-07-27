package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ScheduleTemplateRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull DayOfWeek dayOfWeek,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull LocalTime startTime,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull LocalTime endTime,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @PositiveOrZero Integer totalCapacity,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @PositiveOrZero Integer roundArenaCapacity,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull Map<String, Integer> classCapacities,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive Long expectedConfigVersion,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String reason
) {
}
