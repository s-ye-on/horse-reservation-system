package com.horse.members.presentation.dto;

import java.util.List;

import com.horse.members.application.MemberAvailableRidingClassesResult;
import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.RidingClass;

public record MemberAvailableRidingClassesResponse(
	GeneralRidingGrade currentGeneralGrade,
	boolean dressageApproved,
	boolean jumpingApproved,
	boolean canUseLargeArena,
	List<RidingClass> availableRidingClasses
) {

	public static MemberAvailableRidingClassesResponse from(MemberAvailableRidingClassesResult result) {
		return new MemberAvailableRidingClassesResponse(
			result.currentGeneralGrade(),
			result.dressageApproved(),
			result.jumpingApproved(),
			result.canUseLargeArena(),
			result.availableRidingClasses());
	}

}
