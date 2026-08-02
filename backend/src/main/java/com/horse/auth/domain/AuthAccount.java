package com.horse.auth.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.horse.auth.UserRole;
import com.horse.auth.domain.exception.AuthException;
import com.horse.global.exception.ExceptionCode;

@Entity
@Table(name = "auth_accounts")
public class AuthAccount {

	public static final String INITIAL_ADMIN_BOOTSTRAP_KEY = "INITIAL_ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", unique = true)
	private Long memberId;

	@Column(name = "auth_subject", nullable = false, unique = true, length = 191)
	private String authSubject;

	@Column(name = "normalized_email", nullable = false, unique = true, length = 254)
	private String normalizedEmail;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AuthAccountStatus status;

	@Column(name = "bootstrap_key", unique = true, length = 32)
	private String bootstrapKey;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected AuthAccount() {
	}

	private AuthAccount(
		Long memberId,
		String authSubject,
		String normalizedEmail,
		String passwordHash,
		UserRole role,
		String bootstrapKey
	) {
		this.memberId = memberId;
		this.authSubject = requireText(authSubject, ExceptionCode.AUTH_INVALID_SUBJECT);
		this.normalizedEmail = AuthCredentialPolicy.normalizeEmail(normalizedEmail);
		this.passwordHash = requireText(passwordHash, ExceptionCode.AUTH_INVALID_PASSWORD_HASH);
		this.role = role;
		this.status = AuthAccountStatus.ACTIVE;
		this.bootstrapKey = bootstrapKey;
	}

	public static AuthAccount createMember(
		Long memberId,
		String authSubject,
		String normalizedEmail,
		String passwordHash
	) {
		if (memberId == null) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_MEMBER_REFERENCE);
		}
		return new AuthAccount(memberId, authSubject, normalizedEmail, passwordHash, UserRole.MEMBER, null);
	}

	public static AuthAccount createInitialAdmin(
		String authSubject,
		String normalizedEmail,
		String passwordHash
	) {
		return new AuthAccount(
			null,
			authSubject,
			normalizedEmail,
			passwordHash,
			UserRole.ADMIN,
			INITIAL_ADMIN_BOOTSTRAP_KEY
		);
	}

	private static String requireText(String value, ExceptionCode exceptionCode) {
		if (value == null || value.isBlank()) {
			throw new AuthException(exceptionCode);
		}
		return value;
	}

	public boolean canLogin() {
		return status == AuthAccountStatus.ACTIVE;
	}

	public void deactivate() {
		status = AuthAccountStatus.INACTIVE;
	}

	public void block() {
		status = AuthAccountStatus.BLOCKED;
	}

	public void withdraw() {
		status = AuthAccountStatus.WITHDRAWN;
	}

	public Long getId() {
		return id;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getAuthSubject() {
		return authSubject;
	}

	public String getNormalizedEmail() {
		return normalizedEmail;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public AuthAccountStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
