package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReservationApplicationIdempotencyClaimConcurrencyIntegrationTest {

	private static final String CLAIM_SQL = """
		INSERT IGNORE INTO reservation_application_idempotencies (
			auth_subject, operation, idempotency_key, request_fingerprint, status
		) VALUES (?, 'member_reservation_create', ?, ?, 'processing')
		""";
	private static final String FINGERPRINT =
		"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final long BLOCK_ASSERTION_MILLIS = 300;

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 원장을_초기화한다() {
		jdbcTemplate.update("DELETE FROM reservation_application_idempotencies");
	}

	@AfterEach
	void 생성한_원장을_정리한다() {
		jdbcTemplate.update("DELETE FROM reservation_application_idempotencies");
	}

	@Test
	void 같은_scope의_두번째_claim은_첫_commit을_기다린_뒤_기존_행을_재사용한다()
		throws Exception {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Connection first = dataSource.getConnection()) {
			first.setAutoCommit(false);
			assertThat(claim(first, "commit-subject", "same-key")).isOne();
			final CountDownLatch secondStarted = new CountDownLatch(1);
			final Future<Integer> second = executor.submit(() ->
				claimInNewTransaction("commit-subject", "same-key", secondStarted));
			assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

			assertBlocked(second);
			first.commit();

			assertThat(second.get(5, TimeUnit.SECONDS)).isZero();
			assertThat(ledgerCount("commit-subject", "same-key")).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void 같은_scope의_두번째_claim은_첫_rollback_뒤_새_선점자가_된다()
		throws Exception {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Connection first = dataSource.getConnection()) {
			first.setAutoCommit(false);
			assertThat(claim(first, "rollback-subject", "same-key")).isOne();
			final CountDownLatch secondStarted = new CountDownLatch(1);
			final Future<Integer> second = executor.submit(() ->
				claimInNewTransaction("rollback-subject", "same-key", secondStarted));
			assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

			assertBlocked(second);
			first.rollback();

			assertThat(second.get(5, TimeUnit.SECONDS)).isOne();
			assertThat(ledgerCount("rollback-subject", "same-key")).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void assertBlocked(Future<Integer> future) {
		assertThatThrownBy(() -> future.get(BLOCK_ASSERTION_MILLIS, TimeUnit.MILLISECONDS))
			.isInstanceOf(TimeoutException.class);
	}

	private int claimInNewTransaction(
		String authSubject,
		String idempotencyKey,
		CountDownLatch started
	) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			started.countDown();
			try {
				final int insertedCount = claim(connection, authSubject, idempotencyKey);
				connection.commit();
				return insertedCount;
			}
			catch (SQLException exception) {
				connection.rollback();
				throw exception;
			}
		}
	}

	private int claim(
		Connection connection,
		String authSubject,
		String idempotencyKey
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(CLAIM_SQL)) {
			statement.setString(1, authSubject);
			statement.setString(2, idempotencyKey);
			statement.setString(3, FINGERPRINT);
			return statement.executeUpdate();
		}
	}

	private long ledgerCount(String authSubject, String idempotencyKey) {
		final Long count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservation_application_idempotencies
			WHERE auth_subject = ?
			  AND operation = 'member_reservation_create'
			  AND idempotency_key = ?
			""", Long.class, authSubject, idempotencyKey);
		return count == null ? 0 : count;
	}
}
