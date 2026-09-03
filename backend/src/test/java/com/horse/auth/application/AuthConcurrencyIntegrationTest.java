package com.horse.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.auth.domain.RefreshTokenSessionStatus;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.auth.infrastructure.RefreshTokenSessionRepository;
import com.horse.auth.support.AuthIntegrationTestDataCleaner;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthConcurrencyIntegrationTest {

	private static final String PASSWORD = "concurrency-password-1234";

	@Autowired
	AuthRegistrationService registrationService;

	@Autowired
	AuthSessionService sessionService;

	@Autowired
	InitialAdminBootstrapService bootstrapService;

	@Autowired
	AuthAccountRepository authAccountRepository;

	@Autowired
	RefreshTokenSessionRepository refreshTokenSessionRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void 인증_동시성_데이터를_초기화한다() {
		AuthIntegrationTestDataCleaner.clean(jdbcTemplate);
	}

	@Test
	void 같은_이메일의_동시_회원가입은_정확히_한_건만_생성한다() throws Exception {
		final List<Object> results = runConcurrently(2, () -> {
			try {
				return registrationService.signup(
					"  CONCURRENT@example.com ",
					PASSWORD,
					"동시 가입",
					"010-1111-2222"
				);
			} catch (AuthException exception) {
				return exception.code();
			}
		});

		assertThat(results).filteredOn(AuthAccountResult.class::isInstance).hasSize(1);
		assertThat(results).filteredOn("AUTH_EMAIL_ALREADY_EXISTS"::equals).hasSize(1);
		assertThat(authAccountRepository.count()).isOne();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Long.class)).isOne();
	}

	@Test
	void 같은_Refresh_Token의_동시_회전은_정확히_한_요청만_성공한다() throws Exception {
		registrationService.signup(
			"refresh-race@example.com",
			PASSWORD,
			"회전 경쟁",
			"010-2222-3333"
		);
		final String refreshToken = sessionService.login("refresh-race@example.com", PASSWORD).refreshToken();

		final List<Object> results = runConcurrently(2, () -> {
			try {
				return sessionService.refresh(refreshToken);
			} catch (AuthException exception) {
				return exception.code();
			}
		});

		assertThat(results).filteredOn(AuthTokenResult.class::isInstance).hasSize(1);
		assertThat(results).filteredOn("AUTH_INVALID_REFRESH_TOKEN"::equals).hasSize(1);
		final AuthTokenResult successfulResult = (AuthTokenResult) results.stream()
			.filter(AuthTokenResult.class::isInstance)
			.findFirst()
			.orElseThrow();
		assertThat(refreshTokenSessionRepository.findAll())
			.extracting(session -> session.getStatus())
			.containsExactlyInAnyOrder(RefreshTokenSessionStatus.ROTATED, RefreshTokenSessionStatus.REVOKED);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM refresh_token_sessions WHERE parent_session_id IS NOT NULL",
			Long.class
		)).isOne();
		assertThatThrownBy(() -> sessionService.refresh(successfulResult.refreshToken()))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.code()).isEqualTo("AUTH_INVALID_REFRESH_TOKEN"));
	}

	@Test
	void 최초_관리자_동시_Bootstrap은_한_명만_생성한다() throws Exception {
		final List<Object> results = runConcurrently(2, () -> bootstrapService.bootstrap(
			"initial-admin@example.com",
			PASSWORD
		));

		assertThat(results).containsExactlyInAnyOrder(true, false);
		assertThat(authAccountRepository.count()).isOne();
		assertThat(authAccountRepository.existsByRole(UserRole.ADMIN)).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Long.class)).isZero();
	}

	private List<Object> runConcurrently(int taskCount, Callable<Object> task) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(taskCount);
		final CountDownLatch ready = new CountDownLatch(taskCount);
		final CountDownLatch start = new CountDownLatch(1);
		final List<Future<Object>> futures = new ArrayList<>();
		try {
			for (int index = 0; index < taskCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			ready.await();
			start.countDown();
			final List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}
}
