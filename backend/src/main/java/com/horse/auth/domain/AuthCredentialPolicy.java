package com.horse.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import com.horse.auth.domain.exception.AuthException;
import com.horse.global.exception.ExceptionCode;

public final class AuthCredentialPolicy {

	public static final int MINIMUM_PASSWORD_LENGTH = 8;
	public static final int MAXIMUM_PASSWORD_BYTES = 72;

	private static final int MAXIMUM_EMAIL_LENGTH = 254;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private AuthCredentialPolicy() {
	}

	public static String normalizeEmail(String email) {
		if (email == null) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_EMAIL);
		}
		final String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() > MAXIMUM_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_EMAIL);
		}
		return normalized;
	}

	public static void validatePassword(String password) {
		if (!isSupportedPassword(password)) {
			throw new AuthException(ExceptionCode.AUTH_INVALID_PASSWORD);
		}
	}

	public static boolean isSupportedPassword(String password) {
		return password != null
			&& password.length() >= MINIMUM_PASSWORD_LENGTH
			&& password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_PASSWORD_BYTES;
	}
}
