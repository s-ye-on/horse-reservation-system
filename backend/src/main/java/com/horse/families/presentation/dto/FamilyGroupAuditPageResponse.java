package com.horse.families.presentation.dto;

import java.util.List;

import com.horse.families.application.FamilyGroupAuditPageResult;

public record FamilyGroupAuditPageResponse(
	List<FamilyGroupAuditResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static FamilyGroupAuditPageResponse from(FamilyGroupAuditPageResult result) {
		return new FamilyGroupAuditPageResponse(
			result.content().stream().map(FamilyGroupAuditResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
