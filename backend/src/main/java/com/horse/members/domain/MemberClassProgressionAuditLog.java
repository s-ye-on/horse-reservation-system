package com.horse.members.domain;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

@Entity
@Immutable
@Table(name = "member_class_progression_audit_logs")
public class MemberClassProgressionAuditLog {

	private static final int MAX_ACTOR_LENGTH = 191;
	private static final int MAX_REASON_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 48)
	private MemberClassProgressionAuditAction action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "from_state", columnDefinition = "json", updatable = false)
	private Map<String, Object> fromState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "to_state", nullable = false, columnDefinition = "json", updatable = false)
	private Map<String, Object> toState;

	@Column(name = "actor_auth_subject", nullable = false, updatable = false, length = MAX_ACTOR_LENGTH)
	private String actorAuthSubject;

	@Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
	private String reason;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp(source = SourceType.DB)
	private LocalDateTime createdAt;

	protected MemberClassProgressionAuditLog() {
	}

	private MemberClassProgressionAuditLog(
		Member member,
		MemberClassProgressionAuditAction action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason
	) {
		if (member == null || action == null || toState == null) {
			throw new MemberException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		this.member = member;
		this.action = action;
		this.fromState = fromState == null ? null : Map.copyOf(fromState);
		this.toState = Map.copyOf(toState);
		this.actorAuthSubject = requireText(
			actorAuthSubject,
			MAX_ACTOR_LENGTH,
			ExceptionCode.MEMBER_INVALID_CLASS_CHANGE_ACTOR);
		this.reason = requireText(
			reason,
			MAX_REASON_LENGTH,
			ExceptionCode.MEMBER_INVALID_CLASS_CHANGE_REASON);
	}

	public static MemberClassProgressionAuditLog create(
		Member member,
		MemberClassProgressionAuditAction action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason
	) {
		return new MemberClassProgressionAuditLog(
			member,
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason);
	}

	private static String requireText(String value, int maxLength, ExceptionCode exceptionCode) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new MemberException(exceptionCode);
		}
		return value.strip();
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public MemberClassProgressionAuditAction getAction() {
		return action;
	}

	public Map<String, Object> getFromState() {
		return fromState == null ? null : Map.copyOf(fromState);
	}

	public Map<String, Object> getToState() {
		return Map.copyOf(toState);
	}

	public String getActorAuthSubject() {
		return actorAuthSubject;
	}

	public String getReason() {
		return reason;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
