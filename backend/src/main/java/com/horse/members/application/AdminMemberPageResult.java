package com.horse.members.application;

import java.util.List;

public record AdminMemberPageResult(
	List<AdminMemberQueryResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
