package com.horse.auth.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.auth.application.AuthTokenResult;

public record WebAuthTokenResponse(
	String accessToken,
	String tokenType,
	OffsetDateTime accessTokenExpiresAt
) {
	public static WebAuthTokenResponse from(AuthTokenResult result) {
		return new WebAuthTokenResponse(
			result.accessToken(),
			result.tokenType(),
			result.accessTokenExpiresAt()
		);
	}
}
