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
		previewBaseline(startingClass, actualCompletedRideCount);
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
		validateSpecialApprovalProgressionCredit(correctedCredit);
		specialApprovalProgressionCredit = correctedCredit;
	}

	public void setPromotionHold(
		GeneralRidingGrade holdClass,
		int actualCompletedRideCount
	) {
		previewPromotionHold(holdClass, actualCompletedRideCount);
		promotionHoldClass = holdClass;
	}

	public void removePromotionHold() {
		ensureInitialized();
		promotionHoldClass = null;
	}

	public MemberClassProgressionProjection currentProjection(int actualCompletedRideCount) {
		return projection(
			actualCompletedRideCount,
			baselineClass,
			baselineThreshold,
			baselineActualRideCount,
			specialApprovalProgressionCredit,
			promotionHoldClass);
	}

	MemberClassProgressionProjection previewBaseline(
		GeneralRidingGrade startingClass,
		int actualCompletedRideCount
	) {
		if (startingClass == null || actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROGRESSION_BASELINE);
		}
		return projection(
			actualCompletedRideCount,
			startingClass,
			startingClass.minimumRideCount(),
			actualCompletedRideCount,
			specialApprovalProgressionCredit,
			promotionHoldClass);
	}

	MemberClassProgressionProjection previewWithoutBaseline(int actualCompletedRideCount) {
		ensureInitialized();
		return projection(
			actualCompletedRideCount,
			null,
			null,
			null,
			specialApprovalProgressionCredit,
			promotionHoldClass);
	}

	MemberClassProgressionProjection previewPromotionHold(
		GeneralRidingGrade holdClass,
		int actualCompletedRideCount
	) {
		ensureInitialized();
		if (holdClass == null || holdClass.isHigherThan(progressionClass(actualCompletedRideCount))) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROMOTION_HOLD);
		}
		return projection(
			actualCompletedRideCount,
			baselineClass,
			baselineThreshold,
			baselineActualRideCount,
			specialApprovalProgressionCredit,
			holdClass);
	}

	MemberClassProgressionProjection previewWithoutPromotionHold(int actualCompletedRideCount) {
		ensureInitialized();
		return projection(
			actualCompletedRideCount,
			baselineClass,
			baselineThreshold,
			baselineActualRideCount,
			specialApprovalProgressionCredit,
			null);
	}

	MemberClassProgressionProjection previewSpecialApprovalProgressionCredit(
		int correctedCredit,
		int actualCompletedRideCount
	) {
		ensureInitialized();
		validateSpecialApprovalProgressionCredit(correctedCredit);
		return projection(
			actualCompletedRideCount,
			baselineClass,
			baselineThreshold,
			baselineActualRideCount,
			correctedCredit,
			promotionHoldClass);
	}

	MemberClassProgressionProjection previewActualCompletedRideCount(int actualCompletedRideCount) {
		return currentProjection(actualCompletedRideCount);
	}

	public int progressionValue(int actualCompletedRideCount) {
		if (actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_GENERAL_RIDE_COUNT);
		}
		return actualCompletedRideCount
			+ baselineProgressionCredit()
			+ specialApprovalProgressionCredit;
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

	private void validateSpecialApprovalProgressionCredit(int correctedCredit) {
		if (correctedCredit < 0 || correctedCredit > specialApprovalProgressionCredit) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_PROGRESSION_CREDIT);
		}
	}

	private MemberClassProgressionProjection projection(
		int actualCompletedRideCount,
		GeneralRidingGrade candidateBaselineClass,
		Integer candidateBaselineThreshold,
		Integer candidateBaselineActualRideCount,
		int candidateSpecialApprovalProgressionCredit,
		GeneralRidingGrade candidatePromotionHoldClass
	) {
		if (actualCompletedRideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_GENERAL_RIDE_COUNT);
		}
		final int baselineCredit = candidateBaselineThreshold == null
			? 0
			: Math.max(0, candidateBaselineThreshold - candidateBaselineActualRideCount);
		final int candidateProgressionValue = actualCompletedRideCount
			+ baselineCredit
			+ candidateSpecialApprovalProgressionCredit;
		final GeneralRidingGrade candidateProgressionClass = GeneralRidingGrade.fromRideCount(
			candidateProgressionValue);
		final GeneralRidingGrade candidateEffectiveClass = candidatePromotionHoldClass == null
			? candidateProgressionClass
			: GeneralRidingGrade.lowerOf(candidateProgressionClass, candidatePromotionHoldClass);
		return new MemberClassProgressionProjection(
			actualCompletedRideCount,
			candidateProgressionValue,
			candidateProgressionClass,
			candidateEffectiveClass,
			candidateBaselineClass,
			candidateBaselineThreshold,
			candidateBaselineActualRideCount,
			candidateSpecialApprovalProgressionCredit,
			candidatePromotionHoldClass);
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
