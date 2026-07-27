package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.horse.schedules.application.RecurringHolidayRuleView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"holidayId", "dayOfWeek", "effectiveFrom", "reason", "active", "version"
})
public record RecurringHolidayResponse(
	long holidayId,
	DayOfWeek dayOfWeek,
	LocalDate effectiveFrom,
	LocalDate effectiveTo,
	String reason,
	boolean active,
	long version
) {

	public static RecurringHolidayResponse from(RecurringHolidayRuleView view) {
		return new RecurringHolidayResponse(
			view.id(),
			view.dayOfWeek(),
			view.effectiveFrom(),
			view.effectiveTo(),
			view.reason(),
			view.active(),
			view.version());
	}
}
