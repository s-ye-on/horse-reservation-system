package com.horse.families.presentation.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

public record FamilyGroupAuditStateResponse(
	@Schema(nullable = true)
	Long groupId,
	@Schema(nullable = true)
	String name,
	@Schema(nullable = true)
	String status,
	@Schema(nullable = true)
	List<Long> activeMemberIds,
	@Schema(nullable = true)
	String dissolvedAt,
	@Schema(nullable = true)
	Long membershipId,
	@Schema(nullable = true)
	Long memberId,
	@Schema(nullable = true)
	String joinedAt,
	@Schema(nullable = true)
	String endedAt
) {

	public static FamilyGroupAuditStateResponse from(Map<String, Object> state) {
		if (state == null) {
			return null;
		}
		return new FamilyGroupAuditStateResponse(
			longValue(state.get("groupId")),
			stringValue(state.get("name")),
			stringValue(state.get("status")),
			longValues(state.get("activeMemberIds")),
			stringValue(state.get("dissolvedAt")),
			longValue(state.get("membershipId")),
			longValue(state.get("memberId")),
			stringValue(state.get("joinedAt")),
			stringValue(state.get("endedAt")));
	}

	private static Long longValue(Object value) {
		return value instanceof Number number ? number.longValue() : null;
	}

	private static String stringValue(Object value) {
		return value instanceof String text ? text : null;
	}

	private static List<Long> longValues(Object value) {
		if (!(value instanceof List<?> values)) {
			return null;
		}
		return values.stream()
			.filter(Number.class::isInstance)
			.map(Number.class::cast)
			.map(Number::longValue)
			.toList();
	}
}
