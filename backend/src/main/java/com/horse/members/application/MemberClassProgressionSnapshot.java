package com.horse.members.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.Member;

public record MemberClassProgressionSnapshot(
	int actualCompletedRideCount,
	int progressionValue,
	GeneralRidingGrade progressionClass,
	GeneralRidingGrade effectiveClass,
	LocalDateTime managementStartedAt,
	GeneralRidingGrade baselineClass,
	Integer baselineThreshold,
	Integer baselineActualRideCount,
	int specialApprovalProgressionCredit,
	GeneralRidingGrade promotionHoldClass,
	boolean dressageApproved,
	boolean jumpingApproved
) {

	public static MemberClassProgressionSnapshot from(Member member) {
		return new MemberClassProgressionSnapshot(
			member.getGeneralRideCount(),
			member.progressionValue(),
			member.progressionGeneralRidingGrade(),
			member.effectiveGeneralRidingGrade(),
			member.getProgressionManagementStartedAt(),
			member.getProgressionBaselineClass(),
			member.getProgressionBaselineThreshold(),
			member.getProgressionBaselineActualRideCount(),
			member.getSpecialApprovalProgressionCredit(),
			member.getPromotionHoldClass(),
			member.isDressageApproved(),
			member.isJumpingApproved());
	}

	public Map<String, Object> toAuditState() {
		final Map<String, Object> state = new LinkedHashMap<>();
		state.put("actualCompletedRideCount", actualCompletedRideCount);
		state.put("progressionValue", progressionValue);
		state.put("progressionClass", progressionClass.name());
		state.put("effectiveClass", effectiveClass.name());
		putIfNotNull(state, "managementStartedAt", managementStartedAt);
		putIfNotNull(state, "baselineClass", baselineClass);
		putIfNotNull(state, "baselineThreshold", baselineThreshold);
		putIfNotNull(state, "baselineActualRideCount", baselineActualRideCount);
		state.put("specialApprovalProgressionCredit", specialApprovalProgressionCredit);
		putIfNotNull(state, "promotionHoldClass", promotionHoldClass);
		state.put("dressageApproved", dressageApproved);
		state.put("jumpingApproved", jumpingApproved);
		return Map.copyOf(state);
	}

	public Map<String, Object> toRideCountAdjustmentAuditState(int delta) {
		final Map<String, Object> state = new LinkedHashMap<>(toAuditState());
		state.put("rideCountDelta", delta);
		return Map.copyOf(state);
	}

	private static void putIfNotNull(Map<String, Object> state, String key, Object value) {
		if (value instanceof Enum<?> enumValue) {
			state.put(key, enumValue.name());
		}
		else if (value instanceof LocalDateTime localDateTime) {
			state.put(key, localDateTime.toString());
		}
		else if (value != null) {
			state.put(key, value);
		}
	}
}
