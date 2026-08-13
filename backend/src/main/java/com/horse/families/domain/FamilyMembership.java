package com.horse.families.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.horse.families.domain.exception.FamilyException;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;

@Entity
@Table(name = "family_memberships")
public class FamilyMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_group_id", nullable = false, updatable = false)
	private FamilyGroup familyGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	protected FamilyMembership() {
	}

	private FamilyMembership(FamilyGroup familyGroup, Member member, LocalDateTime joinedAt) {
		if (familyGroup == null || member == null || joinedAt == null) {
			throw new FamilyException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		familyGroup.ensureActive();
		this.familyGroup = familyGroup;
		this.member = member;
		this.joinedAt = joinedAt;
	}

	public static FamilyMembership create(
		FamilyGroup familyGroup,
		Member member,
		LocalDateTime joinedAt
	) {
		return new FamilyMembership(familyGroup, member, joinedAt);
	}

	public void end(LocalDateTime endedAt) {
		if (!isActive()) {
			throw new FamilyException(ExceptionCode.FAMILY_MEMBERSHIP_NOT_FOUND);
		}
		if (endedAt == null || endedAt.isBefore(joinedAt)) {
			throw new FamilyException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		this.endedAt = endedAt;
	}

	public boolean isActive() {
		return endedAt == null;
	}

	public Long getId() {
		return id;
	}

	public FamilyGroup getFamilyGroup() {
		return familyGroup;
	}

	public Member getMember() {
		return member;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}
}
