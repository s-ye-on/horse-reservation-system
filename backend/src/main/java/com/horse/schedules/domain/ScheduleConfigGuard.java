package com.horse.schedules.domain;

import java.time.LocalDateTime;

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
