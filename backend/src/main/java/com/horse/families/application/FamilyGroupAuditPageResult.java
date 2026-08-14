package com.horse.families.application;

import java.util.List;

public record FamilyGroupAuditPageResult(
	List<FamilyGroupAuditResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
