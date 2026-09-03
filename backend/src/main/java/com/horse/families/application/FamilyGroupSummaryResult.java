package com.horse.families.application;

import java.time.OffsetDateTime;

import com.horse.families.domain.FamilyGroup;
import com.horse.families.domain.FamilyGroupStatus;
import com.horse.global.time.ApiDateTime;

public record FamilyGroupSummaryResult(
	long groupId,
	String name,
	FamilyGroupStatus status,
	long activeMemberCount,
	OffsetDateTime createdAt,
	OffsetDateTime dissolvedAt
) {

	public static FamilyGroupSummaryResult from(FamilyGroup group, long activeMemberCount) {
		return new FamilyGroupSummaryResult(
			group.getId(),
			group.getName(),
			group.getStatus(),
			activeMemberCount,
			ApiDateTime.toSeoulOffset(group.getCreatedAt()),
			ApiDateTime.toSeoulOffset(group.getDissolvedAt()));
	}
}
