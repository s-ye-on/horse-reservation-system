package com.horse.members.presentation.dto;

import com.horse.members.application.MemberClassProgressionPreviewAction;
import com.horse.members.domain.GeneralRidingGrade;

import jakarta.validation.constraints.NotNull;

public record MemberClassProgressionPreviewRequest(
	@NotNull MemberClassProgressionPreviewAction action,
	GeneralRidingGrade baselineClass,
	GeneralRidingGrade promotionHoldClass,
	Integer specialApprovalProgressionCredit,
	Integer rideCountDelta
) {
}
