package com.horse.members.presentation.dto;

import com.horse.members.application.AdminMemberQueryResult;

public record AdminMemberResponse(
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

	public static AdminMemberResponse from(AdminMemberQueryResult result) {
		return new AdminMemberResponse(
			result.id(),
			result.name(),
			result.phone(),
			result.generalRideCount(),
			result.dressageRideCount(),
			result.jumpingRideCount(),
			result.dressageApproved(),
			result.jumpingApproved(),
			result.canUseLargeArena());
	}

}
