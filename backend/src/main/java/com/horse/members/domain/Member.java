package com.horse.members.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	@Column(name = "large_arena_allowed", nullable = false)
	private boolean largeArenaAllowed;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Member() {
	}

	private Member(String authSubject, String name, String phone) {
		this.authSubject = requireText(authSubject, "인증 주체");
		this.name = requireText(name, "회원 이름");
		this.phone = requireText(phone, "전화번호");
	}

	public static Member create(String authSubject, String name, String phone) {
		return new Member(authSubject, name, phone);
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
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

	public boolean isLargeArenaAllowed() {
		return largeArenaAllowed;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
