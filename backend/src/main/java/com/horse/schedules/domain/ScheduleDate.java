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

	private static final int MAX_REASON_LENGTH = 500;

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

	public void ensureAppliedConfigVersion(long expectedConfigVersion) {
		if (appliedConfigVersion != expectedConfigVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_INCOMPLETE);
		}
	}

	public boolean startClosing(LocalDate today, String actorAuthSubject, String closureReason) {
		if (today == null || !scheduleDate.isAfter(today)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_CLOSURE_NOT_ALLOWED);
		}
		if (status == ScheduleDateStatus.CLOSING || status == ScheduleDateStatus.CLOSED) {
			return false;
		}
		if (status != ScheduleDateStatus.NORMAL && status != ScheduleDateStatus.OPEN) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_CLOSURE_NOT_ALLOWED);
		}
		resumeStatus = status;
		status = ScheduleDateStatus.CLOSING;
		reason = requireReason(closureReason);
		changedBy = requireActor(actorAuthSubject);
		return true;
	}

	public boolean finalizeClosed(String actorAuthSubject) {
		if (status == ScheduleDateStatus.CLOSED) {
			return false;
		}
		if (status != ScheduleDateStatus.CLOSING) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_NOT_CLOSING);
		}
		status = ScheduleDateStatus.CLOSED;
		resumeStatus = null;
		changedBy = requireActor(actorAuthSubject);
		return true;
	}

	public boolean cancelClosing(String actorAuthSubject, String cancellationReason) {
		if (status == ScheduleDateStatus.NORMAL || status == ScheduleDateStatus.OPEN) {
			return false;
		}
		if (status != ScheduleDateStatus.CLOSING || resumeStatus == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_NOT_CLOSING);
		}
		status = resumeStatus;
		resumeStatus = null;
		reason = requireReason(cancellationReason);
		changedBy = requireActor(actorAuthSubject);
		return true;
	}

	public void ensureReservationInflowAllowed() {
		if (!isReservationInflowAllowed()) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_NOT_RESERVABLE);
		}
	}

	public void ensureMemberCancellationAllowed() {
		if (status == ScheduleDateStatus.CLOSING || status == ScheduleDateStatus.CLOSED) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_NOT_RESERVABLE);
		}
	}

	public void ensureClosureCleanupAllowed() {
		if (status != ScheduleDateStatus.CLOSING) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_DATE_NOT_CLOSING);
		}
	}

	public boolean isReservationInflowAllowed() {
		return status == ScheduleDateStatus.NORMAL || status == ScheduleDateStatus.OPEN;
	}

	private static String requireActor(String actorAuthSubject) {
		if (actorAuthSubject == null || actorAuthSubject.isBlank()) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_ACTOR);
		}
		return actorAuthSubject.strip();
	}

	private static String requireReason(String value) {
		if (value == null || value.isBlank() || value.strip().length() > MAX_REASON_LENGTH) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_REASON);
		}
		return value.strip();
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
