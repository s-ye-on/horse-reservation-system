package com.horse.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ProdJwtSecretConfigurationTest {

	private static final String DEVELOPMENT_SECRET = "local-development-jwt-secret-change-me-32-bytes";
	private static final String PLACEHOLDER_SECRET = "replace-with-at-least-32-byte-jwt-secret";
	private static final String VALID_PROD_SECRET = "m31-prod-test-only-secret-with-more-than-32-bytes";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(ProdJwtSecretConfiguration.class);

	@Test
	void 운영_프로필에서_Secret이_없으면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure(null, output);
	}

	@Test
	void 운영_프로필에서_빈_Secret이면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure("", output);
	}

	@Test
	void 운영_프로필에서_공백_Secret이면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure("   ", output);
	}

	@Test
	void 운영_프로필에서_개발_기본_Secret이면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure(DEVELOPMENT_SECRET, output);
	}

	@Test
	void 운영_프로필에서_알려진_placeholder면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure(PLACEHOLDER_SECRET, output);
	}

	@Test
	void 운영_프로필에서_32바이트보다_짧은_Secret이면_시작에_실패한다(CapturedOutput output) {
		assertStartupFailure("short-test-secret", output);
	}

	@Test
	void 운영_프로필에서_외부_Secret과_실제_설정값이_다르면_시작에_실패한다(CapturedOutput output) {
		contextRunner
			.withPropertyValues(
				"spring.profiles.active=prod",
				"JWT_SECRET=" + VALID_PROD_SECRET,
				"security.jwt.secret=" + DEVELOPMENT_SECRET)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(IllegalStateException.class)
					.hasStackTraceContaining(ProdJwtSecretConfiguration.REQUIRED_SECRET_MESSAGE)
					.hasMessageNotContaining(VALID_PROD_SECRET)
					.hasMessageNotContaining(DEVELOPMENT_SECRET);
				assertThat(output)
					.doesNotContain(VALID_PROD_SECRET)
					.doesNotContain(DEVELOPMENT_SECRET);
			});
	}

	@Test
	void 운영_프로필에서_유효한_외부_Secret이면_시작한다(CapturedOutput output) {
		contextRunner
			.withPropertyValues(
				"spring.profiles.active=prod",
				"JWT_SECRET=" + VALID_PROD_SECRET)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getEnvironment().getProperty("security.jwt.secret"))
					.isEqualTo(VALID_PROD_SECRET);
				assertThat(output).doesNotContain(VALID_PROD_SECRET);
			});
	}

	@Test
	void 개발_프로필은_외부_Secret_없이_기존_기본값으로_시작한다() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean("prodJwtSecretValidator");
			assertThat(context.getEnvironment().getProperty("security.jwt.secret"))
				.isEqualTo(DEVELOPMENT_SECRET);
		});
	}

	@Test
	void OpenAPI_프로필은_운영_Secret_검증을_적용하지_않는다() {
		contextRunner
			.withPropertyValues("spring.profiles.active=openapi")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("prodJwtSecretValidator");
			});
	}

	private void assertStartupFailure(String secret, CapturedOutput output) {
		ApplicationContextRunner runner = contextRunner.withPropertyValues("spring.profiles.active=prod");
		if (secret != null) {
			runner = runner.withPropertyValues("JWT_SECRET=" + secret);
		}

		runner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining(ProdJwtSecretConfiguration.REQUIRED_SECRET_MESSAGE);
			if (secret != null && !secret.isBlank()) {
				assertThat(context.getStartupFailure()).hasMessageNotContaining(secret);
				assertThat(output).doesNotContain(secret);
			}
		});
	}
}
