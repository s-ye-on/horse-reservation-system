package com.horse.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Profile(ProdJwtSecretConfiguration.PROD_PROFILE)
@Configuration(proxyBeanMethods = false)
class ProdJwtSecretConfiguration {

	static final String PROD_PROFILE = "prod";
	static final String REQUIRED_SECRET_MESSAGE =
		"prod 프로필에서는 외부 JWT_SECRET 설정이 필요합니다.";

	private static final int MINIMUM_SECRET_BYTES = 32;
	private static final Set<String> FORBIDDEN_SECRETS = Set.of(
		"local-development-jwt-secret-change-me-32-bytes",
		"replace-with-at-least-32-byte-jwt-secret",
		"change-me",
		"changeme",
		"replace-me",
		"your-jwt-secret",
		"your-secret"
	);

	@Bean
	InitializingBean prodJwtSecretValidator(
		Environment environment,
		@Value("${security.jwt.secret:}") String configuredSecret) {
		return () -> validate(environment.getProperty("JWT_SECRET"), configuredSecret);
	}

	private void validate(String externalSecret, String configuredSecret) {
		if (isInvalid(externalSecret) || !externalSecret.equals(configuredSecret)) {
			throw new IllegalStateException(REQUIRED_SECRET_MESSAGE);
		}
	}

	private boolean isInvalid(String secret) {
		if (secret == null || secret.isBlank()) {
			return true;
		}

		String normalized = secret.trim().toLowerCase(Locale.ROOT);
		return secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES
			|| FORBIDDEN_SECRETS.contains(normalized);
	}
}
