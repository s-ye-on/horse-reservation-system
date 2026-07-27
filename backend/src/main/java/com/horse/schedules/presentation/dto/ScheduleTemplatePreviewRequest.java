package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ScheduleTemplatePreviewRequest(
	Long templateId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull DayOfWeek dayOfWeek,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull LocalTime startTime
) {
}
