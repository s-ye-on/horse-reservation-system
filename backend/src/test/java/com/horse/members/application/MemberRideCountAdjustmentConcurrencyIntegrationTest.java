package com.horse.members.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberRideCountAdjustmentConcurrencyIntegrationTest {

	@Autowired
	AdminMemberClassProgressionService service;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void 기존_테스트_데이터를_정리한다() {
		clearTestData();
	}

	@AfterEach
	void 생성한_테스트_데이터를_정리한다() {
		clearTestData();
	}

	@Test
	void 첫_transaction이_회원_잠금을_보유하면_두번째_보정은_대기하고_직렬_before_after를_남긴다() {
		final long memberId = insertManagedMember();
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch firstAdjusted = new CountDownLatch(1);
		final CountDownLatch releaseFirst = new CountDownLatch(1);
		final CountDownLatch secondStarted = new CountDownLatch(1);
		final CountDownLatch secondCompleted = new CountDownLatch(1);
		try {
			final Future<?> first = executor.submit(() -> transaction().executeWithoutResult(status -> {
				service.adjustActualCompletedRideCount(
					memberId, 1, "m32-07-first-admin", "첫 번째 동시 보정");
				firstAdjusted.countDown();
				await(releaseFirst);
			}));
			await(firstAdjusted);

			final Future<?> second = executor.submit(() -> {
				secondStarted.countDown();
				service.adjustActualCompletedRideCount(
					memberId, 2, "m32-07-second-admin", "두 번째 동시 보정");
				secondCompleted.countDown();
			});
			await(secondStarted);
			assertThat(await(secondCompleted, 300, TimeUnit.MILLISECONDS)).isFalse();

			releaseFirst.countDown();
			get(first);
			get(second);
		}
		finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}

		assertThat(actualCount(memberId)).isEqualTo(23);
		assertThat(auditRows(memberId)).containsExactly(
			new AuditRow(1, 20, 21),
			new AuditRow(2, 21, 23));
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
		try {
			return latch.await(timeout, unit);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private void get(Future<?> future) {
		try {
			future.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private long insertManagedMember() {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, progression_management_started_at
			) VALUES ('m32-07-lock-member', '동시 보정 회원', '010-0000-0000', 20, CURRENT_TIMESTAMP(6))
			""");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private int actualCount(long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?",
			Integer.class,
			memberId);
	}

	private List<AuditRow> auditRows(long memberId) {
		return jdbcTemplate.query("""
			SELECT
				CAST(JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.rideCountDelta')) AS SIGNED),
				CAST(JSON_UNQUOTE(JSON_EXTRACT(from_state, '$.actualCompletedRideCount')) AS SIGNED),
				CAST(JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.actualCompletedRideCount')) AS SIGNED)
			FROM member_class_progression_audit_logs
			WHERE member_id = ? AND action = 'RIDE_COUNT_ADJUSTED'
			ORDER BY id
			""", (resultSet, rowNumber) -> new AuditRow(
			resultSet.getInt(1),
			resultSet.getInt(2),
			resultSet.getInt(3)), memberId);
	}

	private void clearTestData() {
		jdbcTemplate.update("""
			DELETE FROM member_class_progression_audit_logs
			WHERE member_id IN (
				SELECT id FROM members WHERE auth_subject = 'm32-07-lock-member'
			)
			""");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject = 'm32-07-lock-member'");
	}

	private record AuditRow(int delta, int beforeCount, int afterCount) {
	}
}
