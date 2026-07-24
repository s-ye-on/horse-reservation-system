package com.horse.schedules.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.horse.schedules.domain.ScheduleConfigStatus;

public record ScheduleSynchronizationStatus(
	ScheduleConfigStatus status,
	long activeVersion,
	Long pendingVersion,
	LocalDate horizonStart,
	LocalDate horizonEnd,
	long totalDateCount,
	long appliedDateCount,
	LocalDateTime syncStartedAt,
	LocalDateTime lastCompletedAt,
	LocalDateTime lastFailedAt,
	String lastFailureCode,
	String lastFailureSummary,
	boolean longRunning
) {
}
