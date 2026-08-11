package com.horse.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.horse.auth.domain.exception.AuthException;
import com.horse.global.exception.ExceptionCode;

@Component
public class RefreshTokenCodec {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		final byte[] tokenBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(tokenBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
	}

	public String hash(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN);
		}
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new AuthException(ExceptionCode.AUTH_TOKEN_PROCESSING_FAILED);
		}
	}
}
