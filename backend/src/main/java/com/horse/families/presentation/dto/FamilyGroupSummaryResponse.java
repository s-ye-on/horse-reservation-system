package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyGroupSummaryResult;
import com.horse.families.domain.FamilyGroupStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record FamilyGroupSummaryResponse(
	long groupId,
	String name,
	FamilyGroupStatus status,
	long activeMemberCount,
	OffsetDateTime createdAt,
	@Schema(nullable = true)
	OffsetDateTime dissolvedAt
) {

	public static FamilyGroupSummaryResponse from(FamilyGroupSummaryResult result) {
		return new FamilyGroupSummaryResponse(
			result.groupId(),
			result.name(),
			result.status(),
			result.activeMemberCount(),
			result.createdAt(),
			result.dissolvedAt());
	}
}
