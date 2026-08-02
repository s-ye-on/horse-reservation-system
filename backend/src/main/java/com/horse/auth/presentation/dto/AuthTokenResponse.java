package com.horse.auth.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.auth.application.AuthTokenResult;

public record AuthTokenResponse(
	String accessToken,
	String refreshToken,
	String tokenType,
	OffsetDateTime accessTokenExpiresAt,
	OffsetDateTime refreshTokenExpiresAt
) {

	public static AuthTokenResponse from(AuthTokenResult result) {
		return new AuthTokenResponse(
			result.accessToken(),
			result.refreshToken(),
			result.tokenType(),
			result.accessTokenExpiresAt(),
			result.refreshTokenExpiresAt()
		);
	}
}
