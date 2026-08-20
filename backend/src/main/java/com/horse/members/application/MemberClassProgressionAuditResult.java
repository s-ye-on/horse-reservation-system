package com.horse.members.application;

import java.time.LocalDateTime;
import java.util.Map;

import com.horse.members.domain.MemberClassProgressionAuditAction;
import com.horse.members.domain.MemberClassProgressionAuditLog;

public record MemberClassProgressionAuditResult(
	long auditId,
	MemberClassProgressionAuditAction action,
	Map<String, Object> fromState,
	Map<String, Object> toState,
	String actorAuthSubject,
	String reason,
	LocalDateTime occurredAt
) {

	public static MemberClassProgressionAuditResult from(MemberClassProgressionAuditLog auditLog) {
		return new MemberClassProgressionAuditResult(
			auditLog.getId(),
			auditLog.getAction(),
			auditLog.getFromState(),
			auditLog.getToState(),
			auditLog.getActorAuthSubject(),
			auditLog.getReason(),
			auditLog.getCreatedAt());
	}
}
