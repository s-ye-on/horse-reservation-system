package com.horse.auth.application;

import java.time.OffsetDateTime;

public record AuthTokenResult(
	String accessToken,
	String refreshToken,
	String tokenType,
	OffsetDateTime accessTokenExpiresAt,
	OffsetDateTime refreshTokenExpiresAt
) {
}
