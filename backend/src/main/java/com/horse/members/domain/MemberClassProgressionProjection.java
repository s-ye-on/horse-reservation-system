package com.horse.members.domain;

public record MemberClassProgressionProjection(
	int actualCompletedRideCount,
	int progressionValue,
	GeneralRidingGrade progressionClass,
	GeneralRidingGrade effectiveClass,
	GeneralRidingGrade baselineClass,
	Integer baselineThreshold,
	Integer baselineActualRideCount,
	int specialApprovalProgressionCredit,
	GeneralRidingGrade promotionHoldClass
) {
}
