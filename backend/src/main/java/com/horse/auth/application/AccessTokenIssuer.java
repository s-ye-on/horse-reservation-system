package com.horse.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import com.horse.auth.AuthTokenProperties;
import com.horse.auth.domain.AuthAccount;

@Component
public class AccessTokenIssuer {

	private final JwtEncoder jwtEncoder;
	private final AuthTokenProperties properties;
	private final Clock clock;

	public AccessTokenIssuer(JwtEncoder jwtEncoder, AuthTokenProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessTokenValue issue(AuthAccount account) {
		final Instant issuedAt = clock.instant();
		final Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
		final JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.subject(account.getAuthSubject())
			.claim("roles", List.of(account.getRole().name()))
			.build();
		final JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		final String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessTokenValue(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
	}
}
