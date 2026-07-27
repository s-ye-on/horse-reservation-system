package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RecurringHolidayPreviewRequest(
	Long holidayId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull DayOfWeek dayOfWeek,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull LocalDate effectiveFrom,
	LocalDate effectiveTo
) {
}
