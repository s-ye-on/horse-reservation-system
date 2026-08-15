package com.horse.members.presentation.dto;

import java.time.LocalDateTime;

import com.horse.members.application.AdminMemberQueryResult;
import com.horse.members.domain.GeneralRidingGrade;

public record AdminMemberResponse(
	Long id,
	String name,
	String phone,
	int generalRideCount,
	int dressageRideCount,
	int jumpingRideCount,
	int progressionValue,
	GeneralRidingGrade progressionClass,
	GeneralRidingGrade effectiveClass,
	LocalDateTime progressionManagementStartedAt,
	GeneralRidingGrade progressionBaselineClass,
	Integer progressionBaselineThreshold,
	Integer progressionBaselineActualRideCount,
	int specialApprovalProgressionCredit,
	GeneralRidingGrade promotionHoldClass,
	boolean dressageApproved,
	boolean jumpingApproved,
	boolean canUseLargeArena
) {

	public static AdminMemberResponse from(AdminMemberQueryResult result) {
		return new AdminMemberResponse(
			result.id(),
			result.name(),
			result.phone(),
			result.generalRideCount(),
			result.dressageRideCount(),
			result.jumpingRideCount(),
			result.progressionValue(),
			result.progressionClass(),
			result.effectiveClass(),
			result.progressionManagementStartedAt(),
			result.progressionBaselineClass(),
			result.progressionBaselineThreshold(),
			result.progressionBaselineActualRideCount(),
			result.specialApprovalProgressionCredit(),
			result.promotionHoldClass(),
			result.dressageApproved(),
			result.jumpingApproved(),
			result.canUseLargeArena());
	}

}
