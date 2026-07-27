package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.ScheduleOccurrenceSynchronizationResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"targetVersion", "createdCount", "updatedCount", "appliedDateCount",
	"skippedDateCount", "synchronization"
})
public record ScheduleSynchronizationResultResponse(
	long targetVersion,
	int createdCount,
	int updatedCount,
	int appliedDateCount,
	int skippedDateCount,
	ScheduleSynchronizationResponse synchronization
) {

	public static ScheduleSynchronizationResultResponse from(
		ScheduleOccurrenceSynchronizationResult result
	) {
		return new ScheduleSynchronizationResultResponse(
			result.targetVersion(),
			result.createdCount(),
			result.updatedCount(),
			result.appliedDateCount(),
			result.skippedDateCount(),
			ScheduleSynchronizationResponse.from(result.status()));
	}
}
