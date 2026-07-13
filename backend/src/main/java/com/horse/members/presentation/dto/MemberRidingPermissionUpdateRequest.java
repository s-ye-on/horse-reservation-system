package com.horse.members.presentation.dto;

public record MemberRidingPermissionUpdateRequest(
	boolean dressageApproved,
	boolean jumpingApproved
) {
}
