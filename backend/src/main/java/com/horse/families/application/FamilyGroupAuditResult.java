package com.horse.families.application;

import java.time.OffsetDateTime;
import java.util.Map;

import com.horse.families.domain.FamilyGroupAuditAction;
import com.horse.families.domain.FamilyGroupAuditLog;
import com.horse.global.time.ApiDateTime;

public record FamilyGroupAuditResult(
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

	public static FamilyGroupAuditResult from(FamilyGroupAuditLog auditLog) {
		return new FamilyGroupAuditResult(
			auditLog.getId(),
			auditLog.getAction(),
			auditLog.getMember() == null ? null : auditLog.getMember().getId(),
			auditLog.getMember() == null ? null : auditLog.getMember().getName(),
			auditLog.getFromState(),
			auditLog.getToState(),
			auditLog.getActorAuthSubject(),
			auditLog.getReason(),
			ApiDateTime.toSeoulOffset(auditLog.getCreatedAt()));
	}
}
