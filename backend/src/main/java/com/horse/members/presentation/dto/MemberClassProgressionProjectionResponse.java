package com.horse.members.presentation.dto;

import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.MemberClassProgressionProjection;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberClassProgressionProjectionResponse(
	int actualCompletedRideCount,
	int progressionValue,
	GeneralRidingGrade progressionClass,
	GeneralRidingGrade effectiveClass,
	@Schema(nullable = true)
	GeneralRidingGrade baselineClass,
	@Schema(nullable = true)
	Integer baselineThreshold,
	@Schema(nullable = true)
	Integer baselineActualRideCount,
	int specialApprovalProgressionCredit,
	@Schema(nullable = true)
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
