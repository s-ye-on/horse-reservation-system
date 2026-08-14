package com.horse.families.presentation.dto;

import java.util.List;

import com.horse.families.application.FamilyMemberCandidatePageResult;

public record FamilyMemberCandidatePageResponse(
	List<FamilyMemberCandidateResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static FamilyMemberCandidatePageResponse from(FamilyMemberCandidatePageResult result) {
		return new FamilyMemberCandidatePageResponse(
			result.content().stream().map(FamilyMemberCandidateResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
