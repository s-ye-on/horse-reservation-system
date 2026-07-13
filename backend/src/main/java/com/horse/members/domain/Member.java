package com.horse.members.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	public static Member create(String authSubject, String name, String phone) {
		return new Member(authSubject, name, phone);
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

	public boolean canUseLargeArena() {
		final GeneralRidingGrade grade = GeneralRidingGrade.fromRideCount(generalRideCount);
		return grade == GeneralRidingGrade.LARGE_ARENA_BEGINNER
			|| grade == GeneralRidingGrade.LARGE_ARENA_TROT
			|| dressageApproved
			|| jumpingApproved;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
