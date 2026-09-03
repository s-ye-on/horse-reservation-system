package com.horse.members.application;

import java.time.LocalDateTime;

import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.Member;

public record AdminMemberQueryResult(
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

	public static AdminMemberQueryResult from(Member member) {
		return new AdminMemberQueryResult(
			member.getId(),
			member.getName(),
			member.getPhone(),
			member.getGeneralRideCount(),
			member.getDressageRideCount(),
			member.getJumpingRideCount(),
			member.progressionValue(),
			member.progressionGeneralRidingGrade(),
			member.effectiveGeneralRidingGrade(),
			member.getProgressionManagementStartedAt(),
			member.getProgressionBaselineClass(),
			member.getProgressionBaselineThreshold(),
			member.getProgressionBaselineActualRideCount(),
			member.getSpecialApprovalProgressionCredit(),
			member.getPromotionHoldClass(),
			member.isDressageApproved(),
			member.isJumpingApproved(),
			member.canUseLargeArena());
	}

}
