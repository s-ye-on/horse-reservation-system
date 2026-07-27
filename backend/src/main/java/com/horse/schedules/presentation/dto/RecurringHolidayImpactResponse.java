package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.RecurringHolidayImpactPreview;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"previous", "current", "combined"})
public record RecurringHolidayImpactResponse(
	RecurringHolidayImpactCountsResponse previous,
	RecurringHolidayImpactCountsResponse current,
	RecurringHolidayImpactCountsResponse combined
) {

	public static RecurringHolidayImpactResponse from(RecurringHolidayImpactPreview preview) {
		return new RecurringHolidayImpactResponse(
			RecurringHolidayImpactCountsResponse.from(preview.previous()),
			RecurringHolidayImpactCountsResponse.from(preview.current()),
			RecurringHolidayImpactCountsResponse.from(preview.combined()));
	}
}
