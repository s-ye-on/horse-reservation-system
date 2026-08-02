package com.horse.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.auth.AuthTokenProperties;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.domain.AuthCredentialPolicy;
import com.horse.auth.domain.RefreshTokenSession;
import com.horse.auth.domain.RefreshTokenSessionStatus;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.domain.exception.RefreshTokenReuseDetectedException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.auth.infrastructure.RefreshTokenSessionRepository;
import com.horse.global.exception.ExceptionCode;

@Service
public class AuthSessionService {

	private static final String TOKEN_TYPE = "Bearer";
	private static final String DUMMY_PASSWORD = "dummy-password-not-a-login";

	private final PasswordEncoder passwordEncoder;
	private final AuthAccountRepository authAccountRepository;
	private final RefreshTokenSessionRepository refreshTokenSessionRepository;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenCodec refreshTokenCodec;
	private final AuthTokenProperties tokenProperties;
	private final Clock clock;
	private final String dummyPasswordHash;

	public AuthSessionService(
		PasswordEncoder passwordEncoder,
		AuthAccountRepository authAccountRepository,
		RefreshTokenSessionRepository refreshTokenSessionRepository,
		AccessTokenIssuer accessTokenIssuer,
		RefreshTokenCodec refreshTokenCodec,
		AuthTokenProperties tokenProperties,
		Clock clock
	) {
		this.passwordEncoder = passwordEncoder;
		this.authAccountRepository = authAccountRepository;
		this.refreshTokenSessionRepository = refreshTokenSessionRepository;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenCodec = refreshTokenCodec;
		this.tokenProperties = tokenProperties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
	}

	@Transactional
	public AuthTokenResult login(String email, String password) {
		final String normalizedEmail = AuthCredentialPolicy.normalizeEmail(email);
		final AuthAccount account = authAccountRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
		final String storedHash = account == null ? dummyPasswordHash : account.getPasswordHash();
		final boolean passwordMatches = AuthCredentialPolicy.isSupportedPassword(password)
			&& passwordEncoder.matches(password, storedHash);
		if (account == null || !passwordMatches || !account.canLogin()) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_CREDENTIALS);
		}
		return createSession(account);
	}

	@Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
	public AuthTokenResult refresh(String rawRefreshToken) {
		final String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
		final RefreshTokenSession session = refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash)
			.orElseThrow(() -> new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN));
		final LocalDateTime now = now();
		if (session.isRotated()) {
			revokeActiveFamilySessions(session.getFamilyId(), now);
			throw new RefreshTokenReuseDetectedException();
		}
		if (!session.isUsableAt(now)) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN);
		}
		final AuthAccount account = authAccountRepository.findById(session.getAuthAccountId())
			.filter(AuthAccount::canLogin)
			.orElseThrow(() -> new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN));
		final String successorRawToken = refreshTokenCodec.generate();
		final LocalDateTime successorExpiresAt = now.plus(tokenProperties.refreshTokenTtl());
		session.rotate(now);
		refreshTokenSessionRepository.save(session);
		refreshTokenSessionRepository.saveAndFlush(session.createSuccessor(
			refreshTokenCodec.hash(successorRawToken),
			now,
			successorExpiresAt
		));
		return tokenResult(account, successorRawToken, successorExpiresAt);
	}

	private void revokeActiveFamilySessions(String familyId, LocalDateTime now) {
		refreshTokenSessionRepository.findByFamilyIdAndStatusForUpdate(
			familyId,
			RefreshTokenSessionStatus.ACTIVE
		).forEach(session -> session.revoke(now));
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		final String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
		final RefreshTokenSession session = refreshTokenSessionRepository.findByTokenHashForUpdate(tokenHash)
			.orElseThrow(() -> new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN));
		final LocalDateTime now = now();
		if (session.isKnownLogoutReplay()) {
			return;
		}
		if (!session.isUsableAt(now)) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN);
		}
		session.revoke(now);
	}

	private AuthTokenResult createSession(AuthAccount account) {
		final LocalDateTime now = now();
		final LocalDateTime expiresAt = now.plus(tokenProperties.refreshTokenTtl());
		final String rawRefreshToken = refreshTokenCodec.generate();
		refreshTokenSessionRepository.saveAndFlush(RefreshTokenSession.create(
			account.getId(),
			refreshTokenCodec.hash(rawRefreshToken),
			UUID.randomUUID().toString(),
			now,
			expiresAt
		));
		return tokenResult(account, rawRefreshToken, expiresAt);
	}

	private AuthTokenResult tokenResult(
		AuthAccount account,
		String rawRefreshToken,
		LocalDateTime refreshExpiresAt
	) {
		final AccessTokenValue accessToken = accessTokenIssuer.issue(account);
		return new AuthTokenResult(
			accessToken.token(),
			rawRefreshToken,
			TOKEN_TYPE,
			accessToken.expiresAt(),
			toOffset(refreshExpiresAt)
		);
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
	}

	private OffsetDateTime toOffset(LocalDateTime value) {
		return value.atZone(clock.getZone()).toOffsetDateTime();
	}
}
