package com.horse.members.presentation.dto;

import java.util.Map;

import com.horse.members.domain.GeneralRidingGrade;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberClassProgressionAuditStateResponse(
	@Schema(nullable = true)
	Integer actualCompletedRideCount,
	@Schema(nullable = true)
	Integer progressionValue,
	@Schema(nullable = true)
	GeneralRidingGrade progressionClass,
	@Schema(nullable = true)
	GeneralRidingGrade effectiveClass,
	@Schema(nullable = true)
	String managementStartedAt,
	@Schema(nullable = true)
	GeneralRidingGrade baselineClass,
	@Schema(nullable = true)
	Integer baselineThreshold,
	@Schema(nullable = true)
	Integer baselineActualRideCount,
	@Schema(nullable = true)
	Integer specialApprovalProgressionCredit,
	@Schema(nullable = true)
	GeneralRidingGrade promotionHoldClass,
	@Schema(nullable = true)
	Boolean dressageApproved,
	@Schema(nullable = true)
	Boolean jumpingApproved,
	@Schema(nullable = true)
	Integer rideCountDelta
) {

	public static MemberClassProgressionAuditStateResponse from(Map<String, Object> state) {
		if (state == null) {
			return null;
		}
		return new MemberClassProgressionAuditStateResponse(
			integerValue(state.get("actualCompletedRideCount")),
			integerValue(state.get("progressionValue")),
			gradeValue(state.get("progressionClass")),
			gradeValue(state.get("effectiveClass")),
			stringValue(state.get("managementStartedAt")),
			gradeValue(state.get("baselineClass")),
			integerValue(state.get("baselineThreshold")),
			integerValue(state.get("baselineActualRideCount")),
			integerValue(state.get("specialApprovalProgressionCredit")),
			gradeValue(state.get("promotionHoldClass")),
			booleanValue(state.get("dressageApproved")),
			booleanValue(state.get("jumpingApproved")),
			integerValue(state.get("rideCountDelta")));
	}

	private static Integer integerValue(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private static String stringValue(Object value) {
		return value instanceof String text ? text : null;
	}

	private static Boolean booleanValue(Object value) {
		return value instanceof Boolean flag ? flag : null;
	}

	private static GeneralRidingGrade gradeValue(Object value) {
		return value instanceof String grade ? GeneralRidingGrade.valueOf(grade) : null;
	}
}
