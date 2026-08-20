package com.horse.members.presentation.dto;

import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.MemberClassProgressionProjection;

public record MemberClassProgressionProjectionResponse(
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

	public static MemberClassProgressionProjectionResponse from(MemberClassProgressionProjection projection) {
		return new MemberClassProgressionProjectionResponse(
			projection.actualCompletedRideCount(),
			projection.progressionValue(),
			projection.progressionClass(),
			projection.effectiveClass(),
			projection.baselineClass(),
			projection.baselineThreshold(),
			projection.baselineActualRideCount(),
			projection.specialApprovalProgressionCredit(),
			projection.promotionHoldClass());
	}
}
