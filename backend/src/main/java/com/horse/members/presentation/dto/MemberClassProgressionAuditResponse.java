package com.horse.members.presentation.dto;

import java.time.LocalDateTime;

import com.horse.members.application.MemberClassProgressionAuditResult;
import com.horse.members.domain.MemberClassProgressionAuditAction;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberClassProgressionAuditResponse(
	long auditId,
	MemberClassProgressionAuditAction action,
	@Schema(nullable = true)
	MemberClassProgressionAuditStateResponse fromState,
	@Schema(nullable = true)
	MemberClassProgressionAuditStateResponse toState,
	String actorAuthSubject,
	String reason,
	LocalDateTime occurredAt
) {

	public static MemberClassProgressionAuditResponse from(MemberClassProgressionAuditResult result) {
		return new MemberClassProgressionAuditResponse(
			result.auditId(),
			result.action(),
			MemberClassProgressionAuditStateResponse.from(result.fromState()),
			MemberClassProgressionAuditStateResponse.from(result.toState()),
			result.actorAuthSubject(),
			result.reason(),
			result.occurredAt());
	}
}
