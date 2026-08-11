package com.horse.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InitialAdminBootstrapIntegrationTest {

	private static final String PASSWORD = "bootstrap-password-1234";

	@Autowired
	InitialAdminBootstrapService bootstrapService;

	@Autowired
	AuthAccountRepository authAccountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void Bootstrap_테스트_데이터를_초기화한다() {
		jdbcTemplate.update("UPDATE refresh_token_sessions SET parent_session_id = NULL");
		jdbcTemplate.update("DELETE FROM refresh_token_sessions");
		jdbcTemplate.update("DELETE FROM auth_accounts");
	}

	@Test
	void 최초_관리자는_회원과_같은_PasswordEncoder로_생성한다() {
		final boolean created = bootstrapService.bootstrap(" ADMIN@Example.COM ", PASSWORD);

		final AuthAccount account = authAccountRepository.findByNormalizedEmail("admin@example.com").orElseThrow();
		assertThat(created).isTrue();
		assertThat(account.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(account.getMemberId()).isNull();
		assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
	}

	@Test
	void 기존_관리자가_있으면_두_번째_Bootstrap은_정보를_덮어쓰지_않는다() {
		bootstrapService.bootstrap("admin@example.com", PASSWORD);
		final AuthAccount original = authAccountRepository.findByNormalizedEmail("admin@example.com").orElseThrow();
		final String originalHash = original.getPasswordHash();

		final boolean created = bootstrapService.bootstrap(
			"other-admin@example.com",
			"other-bootstrap-password"
		);

		assertThat(created).isFalse();
		assertThat(authAccountRepository.count()).isOne();
		assertThat(authAccountRepository.findByNormalizedEmail("admin@example.com").orElseThrow().getPasswordHash())
			.isEqualTo(originalHash);
	}

	@Test
	void Bootstrap_자격_증명이_없거나_유효하지_않으면_고정된_오류로_실패한다() {
		assertThatThrownBy(() -> bootstrapService.bootstrap("", ""))
			.isInstanceOf(AuthException.class)
			.hasMessage("최초 관리자 Bootstrap 설정이 올바르지 않습니다.")
			.hasMessageNotContaining(PASSWORD);
	}
}
