package com.horse.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.jwt")
public record AuthTokenProperties(
	String issuer,
	Duration accessTokenTtl,
	Duration refreshTokenTtl
) {
}
