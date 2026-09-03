package com.horse.families.application;

import java.util.List;

public record FamilyMemberCandidatePageResult(
	List<FamilyMemberCandidateResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
