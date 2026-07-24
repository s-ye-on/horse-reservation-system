package com.horse.schedules.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.exception.ScheduleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "schedule_dates")
public class ScheduleDate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "schedule_date", nullable = false)
	private LocalDate scheduleDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ScheduleDateStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "resume_status")
	private ScheduleDateStatus resumeStatus;

	@Column(name = "reason")
	private String reason;

	@Column(name = "changed_by")
	private String changedBy;

	@Column(name = "applied_config_version", nullable = false)
	private long appliedConfigVersion;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected ScheduleDate() {
	}

	private ScheduleDate(LocalDate scheduleDate, Long appliedConfigVersion) {
		if (scheduleDate == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE);
		}
		if (appliedConfigVersion == null || appliedConfigVersion < 1) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_CONFIG_VERSION);
		}
		this.scheduleDate = scheduleDate;
		this.status = ScheduleDateStatus.NORMAL;
		this.appliedConfigVersion = appliedConfigVersion;
	}

	public static ScheduleDate create(LocalDate scheduleDate, Long appliedConfigVersion) {
		return new ScheduleDate(scheduleDate, appliedConfigVersion);
	}

	public boolean applyConfigVersion(long configVersion) {
		if (configVersion < 1) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_CONFIG_VERSION);
		}
		if (appliedConfigVersion == configVersion) {
			return false;
		}
		appliedConfigVersion = configVersion;
		return true;
	}

	public Long getId() {
		return id;
	}

	public LocalDate getScheduleDate() {
		return scheduleDate;
	}

	public ScheduleDateStatus getStatus() {
		return status;
	}

	public ScheduleDateStatus getResumeStatus() {
		return resumeStatus;
	}

	public String getReason() {
		return reason;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public long getAppliedConfigVersion() {
		return appliedConfigVersion;
	}

	public long getVersion() {
		return version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
