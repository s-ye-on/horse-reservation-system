package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyGroupSummaryResult;
import com.horse.families.domain.FamilyGroupStatus;

public record FamilyGroupSummaryResponse(
	long groupId,
	String name,
	FamilyGroupStatus status,
	long activeMemberCount,
	OffsetDateTime createdAt,
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
