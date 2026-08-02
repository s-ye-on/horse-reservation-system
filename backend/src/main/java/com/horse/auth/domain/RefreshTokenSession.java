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
import jakarta.persistence.Version;

import com.horse.auth.domain.exception.AuthException;
import com.horse.global.exception.ExceptionCode;

@Entity
@Table(name = "refresh_token_sessions")
public class RefreshTokenSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "auth_account_id", nullable = false)
	private Long authAccountId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "parent_session_id", unique = true)
	private Long parentSessionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RefreshTokenSessionStatus status;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "rotated_at")
	private LocalDateTime rotatedAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected RefreshTokenSession() {
	}

	private RefreshTokenSession(
		Long authAccountId,
		String tokenHash,
		String familyId,
		Long parentSessionId,
		LocalDateTime createdAt,
		LocalDateTime expiresAt
	) {
		if (authAccountId == null || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_SESSION);
		}
		this.authAccountId = authAccountId;
		this.tokenHash = requireText(tokenHash);
		this.familyId = requireText(familyId);
		this.parentSessionId = parentSessionId;
		this.status = RefreshTokenSessionStatus.ACTIVE;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static RefreshTokenSession create(
		Long authAccountId,
		String tokenHash,
		String familyId,
		LocalDateTime createdAt,
		LocalDateTime expiresAt
	) {
		return new RefreshTokenSession(authAccountId, tokenHash, familyId, null, createdAt, expiresAt);
	}

	public RefreshTokenSession createSuccessor(
		String successorHash,
		LocalDateTime successorCreatedAt,
		LocalDateTime successorExpiresAt
	) {
		if (id == null) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_SESSION);
		}
		return new RefreshTokenSession(
			authAccountId,
			successorHash,
			familyId,
			id,
			successorCreatedAt,
			successorExpiresAt
		);
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_SESSION);
		}
		return value;
	}

	public boolean isUsableAt(LocalDateTime now) {
		return status == RefreshTokenSessionStatus.ACTIVE && now.isBefore(expiresAt);
	}

	public boolean isKnownLogoutReplay() {
		return status == RefreshTokenSessionStatus.ROTATED || status == RefreshTokenSessionStatus.REVOKED;
	}

	public void rotate(LocalDateTime now) {
		if (!isUsableAt(now)) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN);
		}
		status = RefreshTokenSessionStatus.ROTATED;
		rotatedAt = now;
	}

	public void revoke(LocalDateTime now) {
		if (status == RefreshTokenSessionStatus.ACTIVE) {
			status = RefreshTokenSessionStatus.REVOKED;
			revokedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getAuthAccountId() {
		return authAccountId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getFamilyId() {
		return familyId;
	}

	public RefreshTokenSessionStatus getStatus() {
		return status;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}
}
