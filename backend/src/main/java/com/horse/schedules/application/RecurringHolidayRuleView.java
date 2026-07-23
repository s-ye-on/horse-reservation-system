package com.horse.schedules.application;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.horse.schedules.domain.RecurringHolidayRule;

public record RecurringHolidayRuleView(
	long id,
	DayOfWeek dayOfWeek,
	LocalDate effectiveFrom,
	LocalDate effectiveTo,
	String reason,
	boolean active,
	long version
) {

	public static RecurringHolidayRuleView from(RecurringHolidayRule rule) {
		return new RecurringHolidayRuleView(
			rule.getId(),
			rule.getDayOfWeek(),
			rule.getEffectiveFrom(),
			rule.getEffectiveTo(),
			rule.getReason(),
			rule.isActive(),
			rule.getVersion());
	}
}
