package com.horse.families.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record FamilyMemberCandidateQueryRequest(
	@Size(max = 100)
	String query,
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
