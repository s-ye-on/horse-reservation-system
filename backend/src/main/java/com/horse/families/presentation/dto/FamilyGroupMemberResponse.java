package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyGroupMemberResult;

public record FamilyGroupMemberResponse(
	long membershipId,
	long memberId,
	String name,
	String phone,
	OffsetDateTime joinedAt
) {

	public static FamilyGroupMemberResponse from(FamilyGroupMemberResult result) {
		return new FamilyGroupMemberResponse(
			result.membershipId(),
			result.memberId(),
			result.name(),
			result.phone(),
			result.joinedAt());
	}
}
