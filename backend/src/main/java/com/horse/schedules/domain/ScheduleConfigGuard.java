package com.horse.schedules.domain;

import java.time.LocalDateTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.exception.ScheduleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "schedule_config_guard")
public class ScheduleConfigGuard {

	public static final byte SINGLETON_ID = 1;
	private static final int MAX_FAILURE_CODE_LENGTH = 100;
	private static final int MAX_FAILURE_SUMMARY_LENGTH = 500;

	@Id
	private Byte id;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ScheduleConfigStatus status;

	@Column(name = "active_version", nullable = false)
	private long activeVersion;

	@Column(name = "pending_version")
	private Long pendingVersion;

	@Column(name = "sync_started_at")
	private LocalDateTime syncStartedAt;

	@Column(name = "sync_started_by")
	private String syncStartedBy;

	@Column(name = "last_completed_at")
	private LocalDateTime lastCompletedAt;

	@Column(name = "last_failed_at")
	private LocalDateTime lastFailedAt;

	@Column(name = "last_failure_code")
	private String lastFailureCode;

	@Column(name = "last_failure_summary")
	private String lastFailureSummary;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected ScheduleConfigGuard() {
	}

	public long beginSynchronization(
		long expectedActiveVersion,
		LocalDateTime startedAt,
		String actorAuthSubject
	) {
		ensureCanBeginSynchronization(expectedActiveVersion);
		if (startedAt == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SYNC_STARTED_AT);
		}
		if (actorAuthSubject == null || actorAuthSubject.isBlank()) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_ACTOR);
		}
		status = ScheduleConfigStatus.SYNCING;
		pendingVersion = activeVersion + 1;
		syncStartedAt = startedAt;
		syncStartedBy = actorAuthSubject;
		lastFailedAt = null;
		lastFailureCode = null;
		lastFailureSummary = null;
		return pendingVersion;
	}

	public void ensureCanBeginSynchronization(long expectedActiveVersion) {
		if (status != ScheduleConfigStatus.ACTIVE) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_SYNC_IN_PROGRESS);
		}
		if (activeVersion != expectedActiveVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);
		}
		if (activeVersion == Long.MAX_VALUE) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_CONFIG_VERSION);
		}
	}

	public void ensurePendingVersion(long expectedPendingVersion) {
		if (status != ScheduleConfigStatus.SYNCING
			|| pendingVersion == null
			|| pendingVersion != expectedPendingVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);
		}
	}

	public void ensureActiveVersion(long expectedActiveVersion) {
		if (status != ScheduleConfigStatus.ACTIVE || activeVersion != expectedActiveVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);
		}
	}

	public boolean completeSynchronization(
		long expectedPendingVersion,
		LocalDateTime completedAt
	) {
		if (activeVersion == expectedPendingVersion) {
			return false;
		}
		if (activeVersion > expectedPendingVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);
		}
		ensurePendingVersion(expectedPendingVersion);
		if (completedAt == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SYNC_STARTED_AT);
		}
		activeVersion = expectedPendingVersion;
		status = ScheduleConfigStatus.ACTIVE;
		pendingVersion = null;
		syncStartedAt = null;
		syncStartedBy = null;
		lastCompletedAt = completedAt;
		lastFailedAt = null;
		lastFailureCode = null;
		lastFailureSummary = null;
		return true;
	}

	public void recordSynchronizationFailure(
		long expectedPendingVersion,
		LocalDateTime failedAt,
		String failureCode,
		String failureSummary
	) {
		if (activeVersion == expectedPendingVersion) {
			return;
		}
		if (activeVersion > expectedPendingVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);
		}
		ensurePendingVersion(expectedPendingVersion);
		if (failedAt == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SYNC_STARTED_AT);
		}
		lastFailedAt = failedAt;
		lastFailureCode = requireFailureText(failureCode, MAX_FAILURE_CODE_LENGTH);
		lastFailureSummary = requireFailureText(failureSummary, MAX_FAILURE_SUMMARY_LENGTH);
	}

	private String requireFailureText(String value, int maximumLength) {
		if (value == null || value.isBlank() || value.length() > maximumLength) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_FAILED);
		}
		return value;
	}

	public byte getId() {
		return id;
	}

	public ScheduleConfigStatus getStatus() {
		return status;
	}

	public long getActiveVersion() {
		return activeVersion;
	}

	public Long getPendingVersion() {
		return pendingVersion;
	}

	public LocalDateTime getSyncStartedAt() {
		return syncStartedAt;
	}

	public String getSyncStartedBy() {
		return syncStartedBy;
	}

	public LocalDateTime getLastCompletedAt() {
		return lastCompletedAt;
	}

	public LocalDateTime getLastFailedAt() {
		return lastFailedAt;
	}

	public String getLastFailureCode() {
		return lastFailureCode;
	}

	public String getLastFailureSummary() {
		return lastFailureSummary;
	}

	public long getVersion() {
		return version;
	}
}
