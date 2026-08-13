package com.horse.families.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.families.application.FamilyGroupView;
import com.horse.families.domain.FamilyGroupStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"groupId", "name", "status", "createdAt"})
public record FamilyGroupResponse(
	long groupId,
	String name,
	FamilyGroupStatus status,
	OffsetDateTime createdAt,
	OffsetDateTime dissolvedAt
) {

	public static FamilyGroupResponse from(FamilyGroupView view) {
		return new FamilyGroupResponse(
			view.groupId(),
			view.name(),
			view.status(),
			view.createdAt(),
			view.dissolvedAt());
	}
}
