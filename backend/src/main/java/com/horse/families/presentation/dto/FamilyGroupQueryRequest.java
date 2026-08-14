package com.horse.families.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.horse.families.domain.FamilyGroupStatus;

public record FamilyGroupQueryRequest(
	@Size(max = 100)
	String query,
	FamilyGroupStatus status,
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
