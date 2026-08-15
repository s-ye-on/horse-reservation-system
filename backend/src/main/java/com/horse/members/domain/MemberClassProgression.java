package com.horse.members.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

@Embeddable
public class MemberClassProgression {

	@Column(name = "progression_management_started_at")
	private LocalDateTime managementStartedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "progression_baseline_class", length = 32)
	private GeneralRidingGrade baselineClass;

	@Column(name = "progression_baseline_threshold")
	private Integer baselineThreshold;

	@Column(name = "progression_baseline_actual_ride_count")
	private Integer baselineActualRideCount;

	@Column(name = "special_approval_progression_credit", nullable = false)
	private int specialApprovalProgressionCredit;

	@Enumerated(EnumType.STRING)
	@Column(name = "promotion_hold_class", length = 32)
	private GeneralRidingGrade promotionHoldClass;

	protected MemberClassProgression() {
	}

	public static MemberClassProgression uninitialized() {
		return new MemberClassProgression();
	}

	public void initialize(LocalDateTime startedAt) {
		if (managementStartedAt != null) {
			return;
		}
		if (startedAt == null) {
			throw new MemberException(ExceptionCode.MEMBER_PROGRESSION_NOT_INITIALIZED);
		}
		managementStartedAt = startedAt;
	}

	public void setBaseline(
		GeneralRidingGrade startingClass,
		int actualCompletedRideCount,
		LocalDateTime startedAt
	) {
		if (startingClass == null || actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROGRESSION_BASELINE);
		}
		initialize(startedAt);
		baselineClass = startingClass;
		baselineThreshold = startingClass.minimumRideCount();
		baselineActualRideCount = actualCompletedRideCount;
	}

	public void removeBaseline() {
		ensureInitialized();
		baselineClass = null;
		baselineThreshold = null;
		baselineActualRideCount = null;
	}

	public int recognizeSpecialApproval(int actualCompletedRideCount) {
		ensureInitialized();
		final int currentProgression = progressionValue(actualCompletedRideCount);
		final int recognized = Math.max(
			0,
			GeneralRidingGrade.LARGE_ARENA_TROT.minimumRideCount() - currentProgression);
		specialApprovalProgressionCredit += recognized;
		return recognized;
	}

	public void correctSpecialApprovalProgressionCredit(int correctedCredit) {
		ensureInitialized();
		if (correctedCredit < 0 || correctedCredit > specialApprovalProgressionCredit) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROGRESSION_CREDIT);
		}
		specialApprovalProgressionCredit = correctedCredit;
	}

	public void setPromotionHold(
		GeneralRidingGrade holdClass,
		int actualCompletedRideCount
	) {
		ensureInitialized();
		if (holdClass == null || holdClass.isHigherThan(progressionClass(actualCompletedRideCount))) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROMOTION_HOLD);
		}
		promotionHoldClass = holdClass;
	}

	public void removePromotionHold() {
		ensureInitialized();
		promotionHoldClass = null;
	}

	public int progressionValue(int actualCompletedRideCount) {
		if (actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_GENERAL_RIDE_COUNT);
		}
		return actualCompletedRideCount
			+ baselineProgressionCredit()
			+ specialApprovalProgressionCredit;
	}

	int progressionValueWithBaseline(
		GeneralRidingGrade startingClass,
		int actualCompletedRideCount
	) {
		if (startingClass == null || actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROGRESSION_BASELINE);
		}
		return actualCompletedRideCount
			+ Math.max(0, startingClass.minimumRideCount() - actualCompletedRideCount)
			+ specialApprovalProgressionCredit;
	}

	int progressionValueWithoutBaseline(int actualCompletedRideCount) {
		if (actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_GENERAL_RIDE_COUNT);
		}
		return actualCompletedRideCount + specialApprovalProgressionCredit;
	}

	public GeneralRidingGrade progressionClass(int actualCompletedRideCount) {
		return GeneralRidingGrade.fromRideCount(progressionValue(actualCompletedRideCount));
	}

	public GeneralRidingGrade effectiveClass(int actualCompletedRideCount) {
		final GeneralRidingGrade progressionClass = progressionClass(actualCompletedRideCount);
		return promotionHoldClass == null
			? progressionClass
			: GeneralRidingGrade.lowerOf(progressionClass, promotionHoldClass);
	}

	private int baselineProgressionCredit() {
		if (baselineThreshold == null) {
			return 0;
		}
		return Math.max(0, baselineThreshold - baselineActualRideCount);
	}

	private void ensureInitialized() {
		if (managementStartedAt == null) {
			throw new MemberException(ExceptionCode.MEMBER_PROGRESSION_NOT_INITIALIZED);
		}
	}

	public boolean isInitialized() {
		return managementStartedAt != null;
	}

	public LocalDateTime getManagementStartedAt() {
		return managementStartedAt;
	}

	public GeneralRidingGrade getBaselineClass() {
		return baselineClass;
	}

	public Integer getBaselineThreshold() {
		return baselineThreshold;
	}

	public Integer getBaselineActualRideCount() {
		return baselineActualRideCount;
	}

	public int getSpecialApprovalProgressionCredit() {
		return specialApprovalProgressionCredit;
	}

	public GeneralRidingGrade getPromotionHoldClass() {
		return promotionHoldClass;
	}
}
