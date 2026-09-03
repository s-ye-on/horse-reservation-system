package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyGroupAuditResult;
import com.horse.families.domain.FamilyGroupAuditAction;

import io.swagger.v3.oas.annotations.media.Schema;

public record FamilyGroupAuditResponse(
	long auditId,
	FamilyGroupAuditAction action,
	@Schema(nullable = true)
	Long memberId,
	@Schema(nullable = true)
	String memberName,
	@Schema(nullable = true)
	FamilyGroupAuditStateResponse fromState,
	@Schema(nullable = true)
	FamilyGroupAuditStateResponse toState,
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
			FamilyGroupAuditStateResponse.from(result.fromState()),
			FamilyGroupAuditStateResponse.from(result.toState()),
			result.actorAuthSubject(),
			result.reason(),
			result.occurredAt());
	}
}
