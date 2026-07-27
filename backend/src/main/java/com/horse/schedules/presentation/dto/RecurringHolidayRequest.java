package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecurringHolidayRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull DayOfWeek dayOfWeek,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull LocalDate effectiveFrom,
	LocalDate effectiveTo,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String holidayReason,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive Long expectedConfigVersion,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String changeReason
) {
}
