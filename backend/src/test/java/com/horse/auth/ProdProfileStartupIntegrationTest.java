package com.horse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.horse.HorseBackendApplication;
import com.horse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.mysql.MySQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"spring.profiles.active=prod",
	"JWT_SECRET=m31-prod-startup-test-only-secret-more-than-32-bytes"
})
@ExtendWith(OutputCaptureExtension.class)
class ProdProfileStartupIntegrationTest {

	private static final String TEST_SECRET = "m31-prod-startup-test-only-secret-more-than-32-bytes";
	private static final String INVALID_PLACEHOLDER = "replace-with-at-least-32-byte-jwt-secret";

	@Autowired
	Environment environment;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 운영_프로필과_외부_Secret을_함께_주입하면_애플리케이션이_시작한다(CapturedOutput output) {
		assertThat(environment.matchesProfiles("prod")).isTrue();
		assertThat(environment.getProperty("security.jwt.secret")).isEqualTo(TEST_SECRET);
		assertThat(jwtDecoder).isNotNull();
		assertThat(output).doesNotContain(TEST_SECRET);
	}

	@Test
	void 운영_프로필과_잘못된_Secret으로_전체_애플리케이션을_시작하면_실패한다(CapturedOutput output) {
		assertThatThrownBy(() -> {
			try (var ignored = new SpringApplicationBuilder(HorseBackendApplication.class)
				.profiles("prod")
				.properties(
					"JWT_SECRET=" + INVALID_PLACEHOLDER,
					"spring.datasource.url=" + mysqlContainer.getJdbcUrl(),
					"spring.datasource.username=" + mysqlContainer.getUsername(),
					"spring.datasource.password=" + mysqlContainer.getPassword(),
					"server.port=0")
				.run()) {
				// The context must fail before this block is entered.
			}
		})
			.hasRootCauseInstanceOf(IllegalStateException.class)
			.hasStackTraceContaining(ProdJwtSecretConfiguration.REQUIRED_SECRET_MESSAGE)
			.hasMessageNotContaining(INVALID_PLACEHOLDER);
		assertThat(output).doesNotContain(INVALID_PLACEHOLDER);
	}
}
