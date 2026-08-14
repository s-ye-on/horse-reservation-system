package com.horse.families.application;

import java.time.OffsetDateTime;

import com.horse.families.domain.FamilyMembership;
import com.horse.global.time.ApiDateTime;

public record FamilyGroupMemberResult(
	long membershipId,
	long memberId,
	String name,
	String phone,
	OffsetDateTime joinedAt
) {

	public static FamilyGroupMemberResult from(FamilyMembership membership) {
		return new FamilyGroupMemberResult(
			membership.getId(),
			membership.getMember().getId(),
			membership.getMember().getName(),
			membership.getMember().getPhone(),
			ApiDateTime.toSeoulOffset(membership.getJoinedAt()));
	}
}
