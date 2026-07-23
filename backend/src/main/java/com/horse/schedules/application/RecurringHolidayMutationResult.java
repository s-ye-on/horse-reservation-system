package com.horse.schedules.application;

public record RecurringHolidayMutationResult(
	RecurringHolidayRuleView rule,
	long pendingConfigVersion,
	RecurringHolidayImpactPreview impact
) {
}
