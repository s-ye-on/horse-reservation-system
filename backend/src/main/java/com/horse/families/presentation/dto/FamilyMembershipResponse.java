package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyMembershipView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"membershipId", "groupId", "memberId", "joinedAt"})
public record FamilyMembershipResponse(
	long membershipId,
	long groupId,
	long memberId,
	OffsetDateTime joinedAt,
	OffsetDateTime endedAt
) {

	public static FamilyMembershipResponse from(FamilyMembershipView view) {
		return new FamilyMembershipResponse(
			view.membershipId(),
			view.groupId(),
			view.memberId(),
			view.joinedAt(),
			view.endedAt());
	}
}
