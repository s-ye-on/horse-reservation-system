package com.horse.schedules.application;

import java.time.LocalDate;

public record ScheduleOccurrenceDateResult(
	LocalDate scheduleDate,
	int createdCount,
	int updatedCount,
	boolean applied,
	boolean skipped
) {

	public static ScheduleOccurrenceDateResult skipped(LocalDate scheduleDate) {
		return new ScheduleOccurrenceDateResult(scheduleDate, 0, 0, false, true);
	}
}
