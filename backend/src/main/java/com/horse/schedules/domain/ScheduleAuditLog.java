package com.horse.schedules.domain;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

@Entity
@Immutable
@Table(name = "schedule_audit_logs")
public class ScheduleAuditLog {

	private static final int MAX_TARGET_KEY_LENGTH = 191;
	private static final int MAX_ACTION_LENGTH = 50;
	private static final int MAX_ACTOR_LENGTH = 191;
	private static final int MAX_REASON_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false)
	private ScheduleAuditTargetType targetType;

	@Column(name = "target_key", nullable = false, updatable = false)
	private String targetKey;

	@Column(name = "action", nullable = false, updatable = false)
	private String action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "from_state", columnDefinition = "json", updatable = false)
	private Map<String, Object> fromState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "to_state", columnDefinition = "json", updatable = false)
	private Map<String, Object> toState;

	@Column(name = "actor_auth_subject", nullable = false, updatable = false)
	private String actorAuthSubject;

	@Column(name = "reason", nullable = false, updatable = false)
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata_json", columnDefinition = "json", updatable = false)
	private Map<String, Object> metadata;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ScheduleAuditLog() {
	}

	private ScheduleAuditLog(
		ScheduleAuditTargetType targetType,
		String targetKey,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason,
		Map<String, Object> metadata
	) {
		if (targetType == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_AUDIT_TARGET);
		}
		this.targetType = targetType;
		this.targetKey = requireText(
			targetKey,
			MAX_TARGET_KEY_LENGTH,
			ExceptionCode.SCHEDULE_INVALID_AUDIT_TARGET);
		this.action = requireText(
			action,
			MAX_ACTION_LENGTH,
			ExceptionCode.SCHEDULE_INVALID_AUDIT_ACTION);
		this.fromState = copyNullable(fromState);
		this.toState = copyNullable(toState);
		this.actorAuthSubject = requireText(
			actorAuthSubject,
			MAX_ACTOR_LENGTH,
			ExceptionCode.SCHEDULE_INVALID_ACTOR);
		this.reason = requireText(
			reason,
			MAX_REASON_LENGTH,
			ExceptionCode.SCHEDULE_INVALID_REASON);
		this.metadata = copyNullable(metadata);
	}

	public static ScheduleAuditLog create(
		ScheduleAuditTargetType targetType,
		String targetKey,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason,
		Map<String, Object> metadata
	) {
		return new ScheduleAuditLog(
			targetType,
			targetKey,
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason,
			metadata);
	}

	private static String requireText(String value, int maxLength, ExceptionCode exceptionCode) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new ScheduleException(exceptionCode);
		}
		return value;
	}

	private static Map<String, Object> copyNullable(Map<String, Object> value) {
		return value == null ? null : Map.copyOf(value);
	}

	public Long getId() {
		return id;
	}

	public ScheduleAuditTargetType getTargetType() {
		return targetType;
	}

	public String getTargetKey() {
		return targetKey;
	}

	public String getAction() {
		return action;
	}

	public Map<String, Object> getFromState() {
		return copyNullable(fromState);
	}

	public Map<String, Object> getToState() {
		return copyNullable(toState);
	}

	public String getActorAuthSubject() {
		return actorAuthSubject;
	}

	public String getReason() {
		return reason;
	}

	public Map<String, Object> getMetadata() {
		return copyNullable(metadata);
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
