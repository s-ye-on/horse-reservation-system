package com.horse.families.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import com.horse.families.domain.exception.FamilyException;
import com.horse.global.exception.ExceptionCode;

@Entity
@Table(name = "family_groups")
public class FamilyGroup {

	private static final int MAX_NAME_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = MAX_NAME_LENGTH)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private FamilyGroupStatus status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@CreationTimestamp(source = SourceType.DB)
	private LocalDateTime createdAt;

	@Column(name = "dissolved_at")
	private LocalDateTime dissolvedAt;

	protected FamilyGroup() {
	}

	private FamilyGroup(String name) {
		this.name = requireName(name);
		this.status = FamilyGroupStatus.ACTIVE;
	}

	public static FamilyGroup create(String name) {
		return new FamilyGroup(name);
	}

	public void ensureActive() {
		if (status != FamilyGroupStatus.ACTIVE) {
			throw new FamilyException(ExceptionCode.FAMILY_GROUP_NOT_ACTIVE);
		}
	}

	public void dissolve(LocalDateTime dissolvedAt) {
		ensureActive();
		if (dissolvedAt == null) {
			throw new FamilyException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		status = FamilyGroupStatus.DISSOLVED;
		this.dissolvedAt = dissolvedAt;
	}

	private static String requireName(String value) {
		if (value == null || value.isBlank() || value.length() > MAX_NAME_LENGTH) {
			throw new FamilyException(ExceptionCode.FAMILY_INVALID_GROUP_NAME);
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public FamilyGroupStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getDissolvedAt() {
		return dissolvedAt;
	}
}
