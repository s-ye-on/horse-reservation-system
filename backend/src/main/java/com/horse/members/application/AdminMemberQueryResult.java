package com.horse.members.application;

import com.horse.members.domain.Member;

public record AdminMemberQueryResult(
	Long id,
	String name,
	String phone,
	int generalRideCount,
	int dressageRideCount,
	int jumpingRideCount,
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
			member.isDressageApproved(),
			member.isJumpingApproved(),
			member.canUseLargeArena());
	}

}
