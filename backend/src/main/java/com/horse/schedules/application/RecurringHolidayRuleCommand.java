package com.horse.schedules.application;

import java.time.DayOfWeek;
import java.time.LocalDate;

public record RecurringHolidayRuleCommand(
	DayOfWeek dayOfWeek,
	LocalDate effectiveFrom,
	LocalDate effectiveTo,
	String holidayReason,
	long expectedConfigVersion,
	String actorAuthSubject,
	String changeReason
) {
}
