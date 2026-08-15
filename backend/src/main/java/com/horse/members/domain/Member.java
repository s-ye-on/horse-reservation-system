package com.horse.members.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

@Entity
@Table(name = "members")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "auth_subject", nullable = false, unique = true, length = 191)
	private String authSubject;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 30)
	private String phone;

	@Column(name = "general_ride_count", nullable = false)
	private int generalRideCount;

	@Column(name = "dressage_ride_count", nullable = false)
	private int dressageRideCount;

	@Column(name = "jumping_ride_count", nullable = false)
	private int jumpingRideCount;

	@Column(name = "dressage_approved", nullable = false)
	private boolean dressageApproved;

	@Column(name = "jumping_approved", nullable = false)
	private boolean jumpingApproved;

	@Embedded
	private MemberClassProgression classProgression = MemberClassProgression.uninitialized();

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Member() {
	}

	private Member(String authSubject, String name, String phone) {
		this.authSubject = requireText(authSubject, ExceptionCode.MEMBER_INVALID_AUTH_SUBJECT);
		this.name = requireText(name, ExceptionCode.MEMBER_INVALID_NAME);
		this.phone = requireText(phone, ExceptionCode.MEMBER_INVALID_PHONE);
	}

	private Member(String authSubject, String name, String phone, LocalDateTime progressionStartedAt) {
		this(authSubject, name, phone);
		classProgression.initialize(progressionStartedAt);
	}

	public static Member create(String authSubject, String name, String phone) {
		return new Member(authSubject, name, phone);
	}

	public static Member createManaged(
		String authSubject,
		String name,
		String phone,
		LocalDateTime progressionStartedAt
	) {
		return new Member(authSubject, name, phone, progressionStartedAt);
	}

	private static String requireText(String value, ExceptionCode exceptionCode) {
		if (value == null || value.isBlank()) {
			throw new MemberException(exceptionCode);
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public String getAuthSubject() {
		return authSubject;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public int getGeneralRideCount() {
		return generalRideCount;
	}

	public int getDressageRideCount() {
		return dressageRideCount;
	}

	public int getJumpingRideCount() {
		return jumpingRideCount;
	}

	public boolean isDressageApproved() {
		return dressageApproved;
	}

	public boolean isJumpingApproved() {
		return jumpingApproved;
	}

	public int changeSpecialApprovals(boolean dressageApproved, boolean jumpingApproved) {
		final boolean activatesApproval = (!this.dressageApproved && dressageApproved)
			|| (!this.jumpingApproved && jumpingApproved);
		if (activatesApproval && classProgression.getPromotionHoldClass() != null) {
			throw new MemberException(ExceptionCode.MEMBER_CLASS_POLICY_CONFLICT);
		}
		final int recognizedCredit = activatesApproval
			? classProgression.recognizeSpecialApproval(generalRideCount)
			: 0;
		this.dressageApproved = dressageApproved;
		this.jumpingApproved = jumpingApproved;
		return recognizedCredit;
	}

	public void increaseGeneralRideCount() {
		generalRideCount++;
	}

	public void increaseDressageRideCount() {
		dressageRideCount++;
	}

	public void increaseJumpingRideCount() {
		jumpingRideCount++;
	}

	public boolean canUseLargeArena() {
		final GeneralRidingGrade grade = effectiveGeneralRidingGrade();
		return !GeneralRidingGrade.LARGE_ARENA_BEGINNER.isHigherThan(grade)
			|| dressageApproved
			|| jumpingApproved;
	}

	public GeneralRidingGrade currentGeneralRidingGrade() {
		return effectiveGeneralRidingGrade();
	}

	public int progressionValue() {
		return classProgression.progressionValue(generalRideCount);
	}

	public GeneralRidingGrade progressionGeneralRidingGrade() {
		return classProgression.progressionClass(generalRideCount);
	}

	public GeneralRidingGrade effectiveGeneralRidingGrade() {
		return classProgression.effectiveClass(generalRideCount);
	}

	public List<RidingClass> availableRidingClasses() {
		final List<RidingClass> availableClasses = new ArrayList<>(
			effectiveGeneralRidingGrade().availableGeneralRidingClasses());
		if (dressageApproved) {
			availableClasses.add(RidingClass.DRESSAGE);
		}
		if (jumpingApproved) {
			availableClasses.add(RidingClass.JUMPING);
		}
		return List.copyOf(availableClasses);
	}

	public void changeProgressionBaseline(
		GeneralRidingGrade startingClass,
		LocalDateTime progressionStartedAt
	) {
		ensureSpecialApprovalProgressionFloor(
			classProgression.progressionValueWithBaseline(startingClass, generalRideCount));
		classProgression.setBaseline(startingClass, generalRideCount, progressionStartedAt);
	}

	public void initializeProgression(LocalDateTime progressionStartedAt) {
		classProgression.initialize(progressionStartedAt);
	}

	public void removeProgressionBaseline() {
		ensureSpecialApprovalProgressionFloor(
			classProgression.progressionValueWithoutBaseline(generalRideCount));
		classProgression.removeBaseline();
	}

	public void changePromotionHold(GeneralRidingGrade holdClass) {
		if (dressageApproved || jumpingApproved) {
			throw new MemberException(ExceptionCode.MEMBER_CLASS_POLICY_CONFLICT);
		}
		classProgression.setPromotionHold(holdClass, generalRideCount);
	}

	public void removePromotionHold() {
		classProgression.removePromotionHold();
	}

	public void correctSpecialApprovalProgressionCredit(int correctedCredit) {
		if (dressageApproved || jumpingApproved) {
			throw new MemberException(ExceptionCode.MEMBER_CLASS_POLICY_CONFLICT);
		}
		classProgression.correctSpecialApprovalProgressionCredit(correctedCredit);
	}

	public boolean isProgressionInitialized() {
		return classProgression.isInitialized();
	}

	public LocalDateTime getProgressionManagementStartedAt() {
		return classProgression.getManagementStartedAt();
	}

	public GeneralRidingGrade getProgressionBaselineClass() {
		return classProgression.getBaselineClass();
	}

	public Integer getProgressionBaselineThreshold() {
		return classProgression.getBaselineThreshold();
	}

	public Integer getProgressionBaselineActualRideCount() {
		return classProgression.getBaselineActualRideCount();
	}

	public int getSpecialApprovalProgressionCredit() {
		return classProgression.getSpecialApprovalProgressionCredit();
	}

	public GeneralRidingGrade getPromotionHoldClass() {
		return classProgression.getPromotionHoldClass();
	}

	private void ensureSpecialApprovalProgressionFloor(int candidateProgressionValue) {
		if ((dressageApproved || jumpingApproved)
			&& candidateProgressionValue < GeneralRidingGrade.LARGE_ARENA_TROT.minimumRideCount()) {
			throw new MemberException(ExceptionCode.MEMBER_SPECIAL_APPROVAL_PROGRESSION_CONFLICT);
		}
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
