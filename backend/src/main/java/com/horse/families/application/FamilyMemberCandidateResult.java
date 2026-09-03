package com.horse.families.application;

import com.horse.members.domain.Member;

public record FamilyMemberCandidateResult(
	long memberId,
	String name,
	String phone
) {

	public static FamilyMemberCandidateResult from(Member member) {
		return new FamilyMemberCandidateResult(member.getId(), member.getName(), member.getPhone());
	}
}
