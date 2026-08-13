package com.horse.families.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.horse.families.domain.FamilyMembership;

public record FamilyMembershipView(
	long membershipId,
	long groupId,
	long memberId,
	OffsetDateTime joinedAt,
	OffsetDateTime endedAt
) {

	private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

	public static FamilyMembershipView from(FamilyMembership membership) {
		return new FamilyMembershipView(
			membership.getId(),
			membership.getFamilyGroup().getId(),
			membership.getMember().getId(),
			membership.getJoinedAt().atOffset(SEOUL_OFFSET),
			membership.getEndedAt() == null
				? null
				: membership.getEndedAt().atOffset(SEOUL_OFFSET));
	}
}
