package com.horse.auth.application;

import java.time.OffsetDateTime;

public record AccessTokenValue(
	String token,
	OffsetDateTime expiresAt
) {
}
