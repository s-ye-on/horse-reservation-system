package com.horse.schedules.application;

public record ScheduleOccurrenceSynchronizationResult(
	long targetVersion,
	int createdCount,
	int updatedCount,
	int appliedDateCount,
	int skippedDateCount,
	ScheduleSynchronizationStatus status
) {
}
