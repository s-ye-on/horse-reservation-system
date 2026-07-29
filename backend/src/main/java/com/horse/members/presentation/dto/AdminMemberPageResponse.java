package com.horse.members.presentation.dto;

import java.util.List;

import com.horse.members.application.AdminMemberPageResult;

public record AdminMemberPageResponse(
	List<AdminMemberResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static AdminMemberPageResponse from(AdminMemberPageResult result) {
		return new AdminMemberPageResponse(
			result.content().stream().map(AdminMemberResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
