package com.horse.members.application;

import java.util.List;

public record MemberClassProgressionAuditPageResult(
	List<MemberClassProgressionAuditResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
