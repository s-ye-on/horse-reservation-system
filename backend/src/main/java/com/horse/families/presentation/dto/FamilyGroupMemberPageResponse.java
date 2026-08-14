package com.horse.families.presentation.dto;

import java.util.List;

import com.horse.families.application.FamilyGroupMemberPageResult;

public record FamilyGroupMemberPageResponse(
	List<FamilyGroupMemberResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static FamilyGroupMemberPageResponse from(FamilyGroupMemberPageResult result) {
		return new FamilyGroupMemberPageResponse(
			result.content().stream().map(FamilyGroupMemberResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
