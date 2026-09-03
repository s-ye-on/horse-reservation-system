package com.horse.families.presentation.dto;

import com.horse.families.application.FamilyMemberCandidateResult;

public record FamilyMemberCandidateResponse(
	long memberId,
	String name,
	String phone
) {

	public static FamilyMemberCandidateResponse from(FamilyMemberCandidateResult result) {
		return new FamilyMemberCandidateResponse(result.memberId(), result.name(), result.phone());
	}
}
