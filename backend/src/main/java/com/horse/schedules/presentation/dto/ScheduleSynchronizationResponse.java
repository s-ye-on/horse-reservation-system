package com.horse.schedules.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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
	Long pendingVersion,
	LocalDate horizonStart,
	LocalDate horizonEnd,
	long totalDateCount,
	long appliedDateCount,
	long remainingDateCount,
	int progressPercent,
	OffsetDateTime syncStartedAt,
	OffsetDateTime lastCompletedAt,
	OffsetDateTime lastFailedAt,
	String lastFailureCode,
	String lastFailureSummary,
	boolean longRunning
) {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

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
			toOffset(status.syncStartedAt()),
			toOffset(status.lastCompletedAt()),
			toOffset(status.lastFailedAt()),
			status.lastFailureCode(),
			status.lastFailureSummary(),
			status.longRunning());
	}

	private static OffsetDateTime toOffset(LocalDateTime value) {
		return value == null ? null : value.atZone(SEOUL_ZONE).toOffsetDateTime();
	}
}
