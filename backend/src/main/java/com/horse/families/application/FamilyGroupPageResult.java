package com.horse.families.application;

import java.util.List;

public record FamilyGroupPageResult(
	List<FamilyGroupSummaryResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
