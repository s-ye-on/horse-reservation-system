package com.horse.families.domain;

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

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;

import com.horse.families.domain.exception.FamilyException;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;

@Entity
@Immutable
@Table(name = "family_group_audit_logs")
public class FamilyGroupAuditLog {

	private static final int MAX_ACTOR_LENGTH = 191;
	private static final int MAX_REASON_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_group_id", nullable = false, updatable = false)
	private FamilyGroup familyGroup;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", updatable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 32)
	private FamilyGroupAuditAction action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "from_state", columnDefinition = "json", updatable = false)
	private Map<String, Object> fromState;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "to_state", columnDefinition = "json", updatable = false)
	private Map<String, Object> toState;

	@Column(name = "actor_auth_subject", nullable = false, updatable = false, length = MAX_ACTOR_LENGTH)
	private String actorAuthSubject;

	@Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
	private String reason;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp(source = SourceType.DB)
	private LocalDateTime createdAt;

	protected FamilyGroupAuditLog() {
	}

	private FamilyGroupAuditLog(
		FamilyGroup familyGroup,
		Member member,
		FamilyGroupAuditAction action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason
	) {
		if (familyGroup == null || action == null) {
			throw new FamilyException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		this.familyGroup = familyGroup;
		this.member = member;
		this.action = action;
		this.fromState = copyNullable(fromState);
		this.toState = copyNullable(toState);
		this.actorAuthSubject = requireText(
			actorAuthSubject,
			MAX_ACTOR_LENGTH,
			ExceptionCode.FAMILY_INVALID_ACTOR);
		this.reason = requireText(reason, MAX_REASON_LENGTH, ExceptionCode.FAMILY_INVALID_REASON);
	}

	public static FamilyGroupAuditLog create(
		FamilyGroup familyGroup,
		Member member,
		FamilyGroupAuditAction action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason
	) {
		return new FamilyGroupAuditLog(
			familyGroup,
			member,
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason);
	}

	private static String requireText(String value, int maxLength, ExceptionCode exceptionCode) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new FamilyException(exceptionCode);
		}
		return value;
	}

	private static Map<String, Object> copyNullable(Map<String, Object> value) {
		return value == null ? null : Map.copyOf(value);
	}

	public Long getId() {
		return id;
	}

	public FamilyGroup getFamilyGroup() {
		return familyGroup;
	}

	public Member getMember() {
		return member;
	}

	public FamilyGroupAuditAction getAction() {
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
