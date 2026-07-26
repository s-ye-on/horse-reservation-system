package com.horse.timeslots.domain;

import java.time.LocalDateTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.exception.TimeSlotException;

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
@Table(name = "time_slot_closures")
public class TimeSlotClosure {

	private static final int MAX_REASON_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "time_slot_id", nullable = false, updatable = false)
	private Long timeSlotId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TimeSlotClosureStatus status;

	@Column(name = "reason", nullable = false, updatable = false)
	private String reason;

	@Column(name = "started_by", nullable = false, updatable = false)
	private String startedBy;

	@Column(name = "started_at", nullable = false, updatable = false)
	private LocalDateTime startedAt;

	@Column(name = "completed_by")
	private String completedBy;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "withdrawn_by")
	private String withdrawnBy;

	@Column(name = "withdrawn_at")
	private LocalDateTime withdrawnAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected TimeSlotClosure() {
	}

	private TimeSlotClosure(
		Long timeSlotId,
		String reason,
		String startedBy,
		LocalDateTime startedAt
	) {
		this.timeSlotId = requireId(timeSlotId);
		this.reason = requireText(reason);
		this.startedBy = requireText(startedBy);
		this.startedAt = requireTime(startedAt);
		this.status = TimeSlotClosureStatus.IN_PROGRESS;
	}

	public static TimeSlotClosure start(
		Long timeSlotId,
		String reason,
		String startedBy,
		LocalDateTime startedAt
	) {
		return new TimeSlotClosure(timeSlotId, reason, startedBy, startedAt);
	}

	public boolean complete(String actor, LocalDateTime occurredAt) {
		if (status == TimeSlotClosureStatus.COMPLETED) {
			return false;
		}
		ensureInProgress();
		status = TimeSlotClosureStatus.COMPLETED;
		completedBy = requireText(actor);
		completedAt = requireTime(occurredAt);
		return true;
	}

	public boolean withdraw(String actor, LocalDateTime occurredAt) {
		if (status == TimeSlotClosureStatus.WITHDRAWN) {
			return false;
		}
		ensureInProgress();
		status = TimeSlotClosureStatus.WITHDRAWN;
		withdrawnBy = requireText(actor);
		withdrawnAt = requireTime(occurredAt);
		return true;
	}

	private void ensureInProgress() {
		if (status != TimeSlotClosureStatus.IN_PROGRESS) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS);
		}
	}

	private static Long requireId(Long value) {
		if (value == null || value < 1) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSURE_REFERENCE);
		}
		return value;
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank() || value.strip().length() > MAX_REASON_LENGTH) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSURE_REASON);
		}
		return value.strip();
	}

	private static LocalDateTime requireTime(LocalDateTime value) {
		if (value == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSURE_TIME);
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public Long getTimeSlotId() {
		return timeSlotId;
	}

	public TimeSlotClosureStatus getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public String getStartedBy() {
		return startedBy;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public String getCompletedBy() {
		return completedBy;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public String getWithdrawnBy() {
		return withdrawnBy;
	}

	public LocalDateTime getWithdrawnAt() {
		return withdrawnAt;
	}

	public long getVersion() {
		return version;
	}
}
