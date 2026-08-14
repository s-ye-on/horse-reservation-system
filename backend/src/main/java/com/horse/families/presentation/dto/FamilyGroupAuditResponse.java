package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.horse.families.application.FamilyGroupAuditResult;
import com.horse.families.domain.FamilyGroupAuditAction;

public record FamilyGroupAuditResponse(
	long auditId,
	FamilyGroupAuditAction action,
	Long memberId,
	String memberName,
	Map<String, Object> fromState,
	Map<String, Object> toState,
	String actorAuthSubject,
	String reason,
	OffsetDateTime occurredAt
) {

	public static FamilyGroupAuditResponse from(FamilyGroupAuditResult result) {
		return new FamilyGroupAuditResponse(
			result.auditId(),
			result.action(),
			result.memberId(),
			result.memberName(),
			result.fromState(),
			result.toState(),
			result.actorAuthSubject(),
			result.reason(),
			result.occurredAt());
	}
}
