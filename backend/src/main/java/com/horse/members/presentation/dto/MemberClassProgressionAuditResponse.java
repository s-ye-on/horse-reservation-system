package com.horse.members.presentation.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.horse.members.application.MemberClassProgressionAuditResult;
import com.horse.members.domain.MemberClassProgressionAuditAction;

public record MemberClassProgressionAuditResponse(
	long auditId,
	MemberClassProgressionAuditAction action,
	Map<String, Object> fromState,
	Map<String, Object> toState,
	String actorAuthSubject,
	String reason,
	LocalDateTime occurredAt
) {

	public static MemberClassProgressionAuditResponse from(MemberClassProgressionAuditResult result) {
		return new MemberClassProgressionAuditResponse(
			result.auditId(),
			result.action(),
			result.fromState(),
			result.toState(),
			result.actorAuthSubject(),
			result.reason(),
			result.occurredAt());
	}
}
