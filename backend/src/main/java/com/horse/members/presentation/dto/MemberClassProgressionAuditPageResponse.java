package com.horse.members.presentation.dto;

import java.util.List;

import com.horse.members.application.MemberClassProgressionAuditPageResult;

public record MemberClassProgressionAuditPageResponse(
	List<MemberClassProgressionAuditResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static MemberClassProgressionAuditPageResponse from(MemberClassProgressionAuditPageResult result) {
		return new MemberClassProgressionAuditPageResponse(
			result.content().stream().map(MemberClassProgressionAuditResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
