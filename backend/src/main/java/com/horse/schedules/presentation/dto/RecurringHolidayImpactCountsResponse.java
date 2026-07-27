package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.RecurringHolidayImpactCounts;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"affectedDateCount", "templateTimeSlotCount", "activeReservationCount"
})
public record RecurringHolidayImpactCountsResponse(
	long affectedDateCount,
	long templateTimeSlotCount,
	long activeReservationCount
) {

	public static RecurringHolidayImpactCountsResponse from(
		RecurringHolidayImpactCounts counts
	) {
		return new RecurringHolidayImpactCountsResponse(
			counts.affectedDateCount(),
			counts.templateTimeSlotCount(),
			counts.activeReservationCount());
	}
}
