package com.horse.members.application;

import java.util.List;

import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.Member;
import com.horse.members.domain.RidingClass;

public record MemberAvailableRidingClassesResult(
	GeneralRidingGrade currentGeneralGrade,
	boolean dressageApproved,
	boolean jumpingApproved,
	boolean canUseLargeArena,
	List<RidingClass> availableRidingClasses
) {

	public static MemberAvailableRidingClassesResult from(Member member) {
		return new MemberAvailableRidingClassesResult(
			member.currentGeneralRidingGrade(),
			member.isDressageApproved(),
			member.isJumpingApproved(),
			member.canUseLargeArena(),
			member.availableRidingClasses());
	}

}
