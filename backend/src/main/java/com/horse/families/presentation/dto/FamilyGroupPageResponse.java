package com.horse.families.presentation.dto;

import java.util.List;

import com.horse.families.application.FamilyGroupPageResult;

public record FamilyGroupPageResponse(
	List<FamilyGroupSummaryResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static FamilyGroupPageResponse from(FamilyGroupPageResult result) {
		return new FamilyGroupPageResponse(
			result.content().stream().map(FamilyGroupSummaryResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
