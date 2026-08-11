package com.horse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.HorseBackendApplication;
import com.horse.TestcontainersConfiguration;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminBootstrapProfileIntegrationTest {

	private static final String EMAIL = "profile-admin@example.com";
	private static final String PASSWORD = "profile-bootstrap-password";

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	AuthAccountRepository authAccountRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@BeforeEach
	@AfterEach
	void Bootstrap_Profile_테스트_데이터를_초기화한다() {
		jdbcTemplate.update("UPDATE refresh_token_sessions SET parent_session_id = NULL");
		jdbcTemplate.update("DELETE FROM refresh_token_sessions");
		jdbcTemplate.update("DELETE FROM auth_accounts");
	}

	@Test
	void 일반_실행에서는_Bootstrap_Runner가_활성화되지_않는다() {
		assertThat(applicationContext.containsBean("initialAdminBootstrapRunner")).isFalse();
		assertThat(authAccountRepository.count()).isZero();
	}

	@Test
	void Bootstrap_Admin_Profile과_외부_자격_증명을_함께_주입하면_최초_관리자를_생성한다(
		CapturedOutput output
	) {
		try (var context = runBootstrapApplication(EMAIL, PASSWORD)) {
			assertThat(context.containsBean("initialAdminBootstrapRunner")).isTrue();
		}

		assertThat(authAccountRepository.findByNormalizedEmail(EMAIL)).isPresent();
		assertThat(output).doesNotContain(PASSWORD);
	}

	@Test
	void Bootstrap_Admin_Profile에서_자격_증명이_없으면_시작에_실패한다(CapturedOutput output) {
		assertThatThrownBy(() -> {
			try (var ignored = runBootstrapApplication("", "")) {
				// The context must fail before this block is entered.
			}
		})
			.isInstanceOf(AuthException.class)
			.hasStackTraceContaining("최초 관리자 Bootstrap 설정이 올바르지 않습니다.")
			.hasMessageNotContaining(PASSWORD);
		assertThat(output).doesNotContain(PASSWORD);
	}

	@Test
	void Bootstrap_Admin_Profile에서_자격_증명_형식이_잘못되면_시작에_실패한다(CapturedOutput output) {
		assertThatThrownBy(() -> {
			try (var ignored = runBootstrapApplication("invalid-email", "short")) {
				// The context must fail before this block is entered.
			}
		})
			.isInstanceOf(AuthException.class)
			.hasStackTraceContaining("최초 관리자 Bootstrap 설정이 올바르지 않습니다.")
			.hasMessageNotContaining("short");
		assertThat(output).doesNotContain("short");
	}

	private ConfigurableApplicationContext runBootstrapApplication(String email, String password) {
		return new SpringApplicationBuilder(HorseBackendApplication.class)
			.profiles("bootstrap-admin")
			.run(
				"--server.port=0",
				"--spring.datasource.url=" + mysqlContainer.getJdbcUrl(),
				"--spring.datasource.username=" + mysqlContainer.getUsername(),
				"--spring.datasource.password=" + mysqlContainer.getPassword(),
				"--BOOTSTRAP_ADMIN_EMAIL=" + email,
				"--BOOTSTRAP_ADMIN_PASSWORD=" + password
			);
	}
}
