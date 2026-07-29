package com.horse.schedules.presentation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.application.ScheduleSynchronizationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"status", "activeVersion", "horizonStart", "horizonEnd", "totalDateCount",
	"appliedDateCount", "remainingDateCount", "progressPercent", "longRunning"
})
public record ScheduleSynchronizationResponse(
	ScheduleConfigStatus status,
	long activeVersion,
	@Schema(nullable = true) Long pendingVersion,
	@Schema(nullable = true) LocalDate horizonStart,
	@Schema(nullable = true) LocalDate horizonEnd,
	long totalDateCount,
	long appliedDateCount,
	long remainingDateCount,
	int progressPercent,
	@Schema(nullable = true) OffsetDateTime syncStartedAt,
	@Schema(nullable = true) OffsetDateTime lastCompletedAt,
	@Schema(nullable = true) OffsetDateTime lastFailedAt,
	@Schema(nullable = true) String lastFailureCode,
	@Schema(nullable = true) String lastFailureSummary,
	boolean longRunning
) {

	public static ScheduleSynchronizationResponse from(ScheduleSynchronizationStatus status) {
		final long remainingCount = Math.max(
			status.totalDateCount() - status.appliedDateCount(),
			0);
		final int progress = status.totalDateCount() == 0
			? 100
			: (int) Math.min(
				100,
				status.appliedDateCount() * 100 / status.totalDateCount());
		return new ScheduleSynchronizationResponse(
			status.status(),
			status.activeVersion(),
			status.pendingVersion(),
			status.horizonStart(),
			status.horizonEnd(),
			status.totalDateCount(),
			status.appliedDateCount(),
			remainingCount,
			progress,
			ApiDateTime.toSeoulOffset(status.syncStartedAt()),
			ApiDateTime.toSeoulOffset(status.lastCompletedAt()),
			ApiDateTime.toSeoulOffset(status.lastFailedAt()),
			status.lastFailureCode(),
			status.lastFailureSummary(),
			status.longRunning());
	}

}
