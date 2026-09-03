package com.horse.families.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.horse.families.domain.FamilyGroup;
import com.horse.families.domain.FamilyGroupStatus;

public record FamilyGroupView(
	long groupId,
	String name,
	FamilyGroupStatus status,
	OffsetDateTime createdAt,
	OffsetDateTime dissolvedAt
) {

	private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

	public static FamilyGroupView from(FamilyGroup familyGroup) {
		return new FamilyGroupView(
			familyGroup.getId(),
			familyGroup.getName(),
			familyGroup.getStatus(),
			atSeoulOffset(familyGroup.getCreatedAt()),
			atSeoulOffset(familyGroup.getDissolvedAt()));
	}

	private static OffsetDateTime atSeoulOffset(LocalDateTime value) {
		return value == null ? null : value.atOffset(SEOUL_OFFSET);
	}
}
