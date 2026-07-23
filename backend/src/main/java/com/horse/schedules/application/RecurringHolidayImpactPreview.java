package com.horse.schedules.application;

public record RecurringHolidayImpactPreview(
	RecurringHolidayImpactCounts previous,
	RecurringHolidayImpactCounts current,
	RecurringHolidayImpactCounts combined
) {
}
