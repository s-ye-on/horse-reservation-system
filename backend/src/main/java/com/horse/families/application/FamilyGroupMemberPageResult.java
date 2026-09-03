package com.horse.families.application;

import java.util.List;

public record FamilyGroupMemberPageResult(
	List<FamilyGroupMemberResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
