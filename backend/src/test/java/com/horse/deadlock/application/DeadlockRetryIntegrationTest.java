package com.horse.deadlock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

@Import({
	TestcontainersConfiguration.class,
	DeadlockRetryIntegrationTest.RetryProbeConfiguration.class
})
@SpringBootTest
class DeadlockRetryIntegrationTest {

	private static final int MYSQL_DEADLOCK_ERROR_CODE = 1213;
	private static final int MYSQL_LOCK_WAIT_TIMEOUT_ERROR_CODE = 1205;
	private static final String MYSQL_DEADLOCK_SQL_STATE = "40001";
	private static final String MYSQL_LOCK_WAIT_TIMEOUT_SQL_STATE = "HY000";
	private static final int EXPECTED_MAX_ATTEMPTS = 3;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RetryProbeService retryProbeService;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void 재시도_검증_상태를_초기화한다() {
		clearDatabase();
		retryProbeService.reset();
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 실제_MySQL_deadlock을_새_트랜잭션으로_재시도해_효과를_한_번만_반영한다() {
		final CompletionFixture first = insertCompletionFixture("m31-07-deadlock-first");
		final CompletionFixture second = insertCompletionFixture("m31-07-deadlock-second");
		retryProbeService.prepareDeadlockBarrier();

		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final Future<?> firstCommand = executor.submit(() -> retryProbeService.completeWithDeadlock(
				"first-command",
				first,
				second.memberId()));
			final Future<?> secondCommand = executor.submit(() -> retryProbeService.completeWithDeadlock(
				"second-command",
				second,
				first.memberId()));

			getResult(firstCommand);
			getResult(secondCommand);
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(retryProbeService.totalInvocationCount()).isEqualTo(3);
		assertThat(List.of(
			retryProbeService.invocationCount("first-command"),
			retryProbeService.invocationCount("second-command")))
			.containsExactlyInAnyOrder(1, 2);
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsExactlyInAnyOrder(
				TransactionSynchronization.STATUS_COMMITTED,
				TransactionSynchronization.STATUS_COMMITTED,
				TransactionSynchronization.STATUS_ROLLED_BACK);
		assertThat(retryProbeService.persistenceContextCount()).isEqualTo(3);
		assertCompletionEffects(first);
		assertCompletionEffects(second);
	}

	@Test
	void MySQL_deadlock은_최초_실행을_포함해_세_번만_시도하고_모두_롤백한다() {
		assertThatThrownBy(() -> retryProbeService.alwaysFailWithSqlError(
			"m31-07-deadlock-exhausted",
			MYSQL_DEADLOCK_SQL_STATE,
			MYSQL_DEADLOCK_ERROR_CODE))
			.isInstanceOf(CannotAcquireLockException.class);

		assertThat(retryProbeService.invocationCount("m31-07-deadlock-exhausted"))
			.isEqualTo(EXPECTED_MAX_ATTEMPTS);
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsOnly(TransactionSynchronization.STATUS_ROLLED_BACK)
			.hasSize(EXPECTED_MAX_ATTEMPTS);
		assertThat(retryProbeService.persistenceContextCount()).isEqualTo(EXPECTED_MAX_ATTEMPTS);
		assertThat(memberCount("m31-07-deadlock-exhausted")).isZero();
	}

	@Test
	void package_private_트랜잭션도_MySQL_deadlock을_세_번만_시도한다() {
		assertThatThrownBy(() -> retryProbeService.alwaysFailWithPackagePrivateTransaction(
			"m31-07-package-private"))
			.isInstanceOf(CannotAcquireLockException.class);

		assertThat(retryProbeService.invocationCount("m31-07-package-private"))
			.isEqualTo(EXPECTED_MAX_ATTEMPTS);
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsOnly(TransactionSynchronization.STATUS_ROLLED_BACK)
			.hasSize(EXPECTED_MAX_ATTEMPTS);
		assertThat(retryProbeService.persistenceContextCount()).isEqualTo(EXPECTED_MAX_ATTEMPTS);
		assertThat(memberCount("m31-07-package-private")).isZero();
	}

	@Test
	void lock_wait_timeout은_재시도하지_않는다() {
		assertThatThrownBy(() -> retryProbeService.alwaysFailWithSqlError(
			"m31-07-lock-timeout",
			MYSQL_LOCK_WAIT_TIMEOUT_SQL_STATE,
			MYSQL_LOCK_WAIT_TIMEOUT_ERROR_CODE))
			.isInstanceOf(CannotAcquireLockException.class);

		assertThat(retryProbeService.invocationCount("m31-07-lock-timeout")).isOne();
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
		assertThat(memberCount("m31-07-lock-timeout")).isZero();
	}

	@Test
	void 업무_예외는_재시도하지_않는다() {
		assertThatThrownBy(() -> retryProbeService.alwaysFailWithBusinessException(
			"m31-07-business-exception"))
			.isInstanceOf(MemberException.class);

		assertThat(retryProbeService.invocationCount("m31-07-business-exception")).isOne();
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
		assertThat(memberCount("m31-07-business-exception")).isZero();
	}

	@Test
	void 이미_활성화된_외부_트랜잭션에서는_내부_Service가_자체_재시도하지_않는다() {
		final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
			retryProbeService.alwaysFailWithSqlError(
				"m31-07-active-transaction",
				MYSQL_DEADLOCK_SQL_STATE,
				MYSQL_DEADLOCK_ERROR_CODE)))
			.isInstanceOf(CannotAcquireLockException.class);

		assertThat(retryProbeService.invocationCount("m31-07-active-transaction")).isOne();
		assertThat(retryProbeService.transactionCompletionStatuses())
			.containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
		assertThat(retryProbeService.persistenceContextCount()).isOne();
		assertThat(memberCount("m31-07-active-transaction")).isZero();
	}

	private CompletionFixture insertCompletionFixture(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, large_arena_allowed
			) VALUES (?, '재시도 회원', '010-0000-0000', 0, FALSE)
			""", authSubject);
		final Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'm31-07-test')
			""", memberId);
		final Long couponId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, coupon_id, approval_requested_at, admin_confirmed_at
			) VALUES (
				?, 'FIRST_RIDE', '2026-08-01', '09:00:00', '09:45:00', 'confirmed',
				'coupon', ?, '2026-07-20 09:00:00', '2026-07-20 10:00:00'
			)
			""", memberId, couponId);
		final Long reservationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new CompletionFixture(memberId, couponId, reservationId);
	}

	private void assertCompletionEffects(CompletionFixture fixture) {
		assertThat(jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?",
			Integer.class,
			fixture.memberId())).isOne();
		assertThat(jdbcTemplate.queryForMap(
			"SELECT remaining_count, held_count FROM coupons WHERE id = ?",
			fixture.couponId()))
			.containsEntry("remaining_count", 9L)
			.containsEntry("held_count", 0L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?",
			String.class,
			fixture.reservationId())).isEqualTo("completed");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservations
			WHERE id = ?
			  AND active_slot_guard = 1
			""", Integer.class, fixture.reservationId())).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE reservation_id = ?
			  AND action = 'used'
			  AND count_delta = -1
			""", Integer.class, fixture.reservationId())).isOne();
	}

	private int memberCount(String authSubject) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM members WHERE auth_subject = ?",
			Integer.class,
			authSubject);
	}

	private void getResult(Future<?> future) {
		try {
			future.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private void clearDatabase() {
		jdbcTemplate.update("""
			DELETE usage_log
			FROM coupon_usage_logs usage_log
			JOIN members member ON member.id = usage_log.member_id
			WHERE member.auth_subject LIKE 'm31-07-%'
			""");
		jdbcTemplate.update("""
			DELETE reservation
			FROM reservations reservation
			JOIN members member ON member.id = reservation.member_id
			WHERE member.auth_subject LIKE 'm31-07-%'
			""");
		jdbcTemplate.update("""
			DELETE coupon
			FROM coupons coupon
			JOIN members member ON member.id = coupon.member_id
			WHERE member.auth_subject LIKE 'm31-07-%'
			""");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'm31-07-%'");
	}

	private record CompletionFixture(Long memberId, Long couponId, Long reservationId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RetryProbeConfiguration {

		@Bean
		RetryProbeService retryProbeService(
			JdbcTemplate jdbcTemplate,
			EntityManagerFactory entityManagerFactory
		) {
			return new RetryProbeService(jdbcTemplate, entityManagerFactory);
		}
	}

	static class RetryProbeService {

		private final JdbcTemplate jdbcTemplate;
		private final EntityManagerFactory entityManagerFactory;
		private final Map<String, AtomicInteger> invocationCounts = new ConcurrentHashMap<>();
		private final List<Integer> transactionCompletionStatuses = new CopyOnWriteArrayList<>();
		private final Set<EntityManager> persistenceContexts = Collections.synchronizedSet(
			Collections.newSetFromMap(new IdentityHashMap<>()));
		private CyclicBarrier deadlockBarrier;

		RetryProbeService(JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
			this.jdbcTemplate = jdbcTemplate;
			this.entityManagerFactory = entityManagerFactory;
		}

		@Transactional
		public void completeWithDeadlock(
			String commandKey,
			CompletionFixture fixture,
			Long secondMemberId
		) {
			final int attempt = registerAttempt(commandKey);
			lockMember(fixture.memberId());
			if (attempt == 1) {
				awaitDeadlockPeer();
			}

			jdbcTemplate.update("""
				UPDATE reservations
				SET status = 'completed'
				WHERE id = ?
				  AND status = 'confirmed'
				""", fixture.reservationId());
			jdbcTemplate.update("""
				UPDATE coupons
				SET remaining_count = remaining_count - 1,
					held_count = held_count - 1
				WHERE id = ?
				  AND held_count > 0
				""", fixture.couponId());
			jdbcTemplate.update("""
				UPDATE members
				SET general_ride_count = general_ride_count + 1
				WHERE id = ?
				""", fixture.memberId());
			jdbcTemplate.update("""
				INSERT INTO coupon_usage_logs (
					coupon_id, reservation_id, member_id, coupon_owner_member_id, action, count_delta,
					occurred_at, actor_type, memo
				) VALUES (?, ?, ?, ?, 'used', -1, CURRENT_TIMESTAMP(6), 'admin', 'm31-07 retry')
				""", fixture.couponId(), fixture.reservationId(), fixture.memberId(), fixture.memberId());

			lockMember(secondMemberId);
		}

		@Transactional
		public void alwaysFailWithSqlError(String authSubject, String sqlState, int errorCode) {
			registerAttempt(authSubject);
			insertRetryMember(authSubject);
			throw new CannotAcquireLockException(
				"m31-07 retry probe",
				new SQLException("m31-07 sql failure", sqlState, errorCode));
		}

		@Transactional
		public void alwaysFailWithBusinessException(String authSubject) {
			registerAttempt(authSubject);
			insertRetryMember(authSubject);
			throw new MemberException(ExceptionCode.MEMBER_NOT_FOUND);
		}

		@Transactional
		void alwaysFailWithPackagePrivateTransaction(String authSubject) {
			registerAttempt(authSubject);
			insertRetryMember(authSubject);
			throw new CannotAcquireLockException(
				"m31-07 package-private retry probe",
				new SQLException(
					"m31-07 sql failure",
					MYSQL_DEADLOCK_SQL_STATE,
					MYSQL_DEADLOCK_ERROR_CODE));
		}

		void prepareDeadlockBarrier() {
			deadlockBarrier = new CyclicBarrier(2);
		}

		void reset() {
			invocationCounts.clear();
			transactionCompletionStatuses.clear();
			persistenceContexts.clear();
			deadlockBarrier = null;
		}

		int invocationCount(String commandKey) {
			return invocationCounts.get(commandKey).get();
		}

		int totalInvocationCount() {
			return invocationCounts.values().stream()
				.mapToInt(AtomicInteger::get)
				.sum();
		}

		List<Integer> transactionCompletionStatuses() {
			return new ArrayList<>(transactionCompletionStatuses);
		}

		int persistenceContextCount() {
			return persistenceContexts.size();
		}

		private int registerAttempt(String commandKey) {
			final EntityManager entityManager =
				EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
			if (entityManager != null) {
				persistenceContexts.add(entityManager);
			}
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					transactionCompletionStatuses.add(status);
				}
			});
			return invocationCounts.computeIfAbsent(commandKey, key -> new AtomicInteger())
				.incrementAndGet();
		}

		private void lockMember(Long memberId) {
			jdbcTemplate.queryForObject(
				"SELECT id FROM members WHERE id = ? FOR UPDATE",
				Long.class,
				memberId);
		}

		private void awaitDeadlockPeer() {
			try {
				deadlockBarrier.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError("deadlock barrier interrupted", exception);
			}
			catch (BrokenBarrierException | TimeoutException exception) {
				throw new AssertionError("deadlock barrier failed", exception);
			}
		}

		private void insertRetryMember(String authSubject) {
			jdbcTemplate.update("""
				INSERT INTO members (
					auth_subject, name, phone, general_ride_count, large_arena_allowed
				) VALUES (?, '재시도 회원', '010-0000-0000', 0, FALSE)
				""", authSubject);
		}
	}
}
