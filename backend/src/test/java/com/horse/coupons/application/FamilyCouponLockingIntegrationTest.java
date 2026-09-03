package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import com.horse.members.domain.RidingClass;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FamilyCouponLockingIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 20);

	@Autowired
	CouponSelectionService selectionService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		deleteTestData();
	}

	@AfterEach
	void 데이터베이스를_정리한다() {
		deleteTestData();
	}

	@Test
	void 갱신_선택은_결정된_Coupon_한_행만_잠근다() throws Exception {
		final long memberId = insertMember();
		final long selectedCouponId = insertCoupon(
			memberId,
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 7, 1, 9, 0));
		final long unselectedCouponId = insertCoupon(
			memberId,
			LocalDateTime.of(2026, 10, 1, 9, 0),
			LocalDateTime.of(2026, 7, 2, 9, 0));
		final CountDownLatch selected = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(3);

		try {
			final Future<Long> holder = executor.submit(() -> transaction().execute(status -> {
				final long couponId = selectionService.selectForUpdate(
					memberId,
					RidingClass.FIRST_RIDE,
					LESSON_DATE).orElseThrow().couponId();
				selected.countDown();
				await(release);
				return couponId;
			}));
			assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();

			final Future<Integer> unselectedUpdate = executor.submit(() -> transaction().execute(
				status -> touchCoupon(unselectedCouponId)));
			assertThat(unselectedUpdate.get(2, TimeUnit.SECONDS)).isOne();

			final Future<Integer> selectedUpdate = executor.submit(() -> transaction().execute(
				status -> touchCoupon(selectedCouponId)));
			assertThatThrownBy(() -> selectedUpdate.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			release.countDown();
			assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo(selectedCouponId);
			assertThat(selectedUpdate.get(5, TimeUnit.SECONDS)).isOne();
		}
		finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void 가족_선택은_접근할_수_없는_선행_Coupon과_후순위_Coupon을_잠그지_않는다() throws Exception {
		final long requesterId = insertMember("family-lock-requester");
		final long selectedOwnerId = insertMember("family-lock-selected-owner");
		final long laterOwnerId = insertMember("family-lock-later-owner");
		final long outsiderId = insertMember("family-lock-outsider");
		final long groupId = insertGroup();
		insertMembership(groupId, requesterId);
		insertMembership(groupId, selectedOwnerId);
		insertMembership(groupId, laterOwnerId);
		final long inaccessibleCouponId = insertCoupon(
			outsiderId,
			LocalDateTime.of(2026, 8, 25, 9, 0),
			LocalDateTime.of(2026, 7, 1, 9, 0));
		final long selectedCouponId = insertCoupon(
			selectedOwnerId,
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 7, 2, 9, 0));
		final long laterCouponId = insertCoupon(
			laterOwnerId,
			LocalDateTime.of(2026, 10, 1, 9, 0),
			LocalDateTime.of(2026, 7, 3, 9, 0));
		final CountDownLatch selected = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(4);

		try {
			final Future<Long> holder = executor.submit(() -> transaction().execute(status -> {
				final long couponId = selectionService.selectForUpdate(
					requesterId,
					RidingClass.FIRST_RIDE,
					LESSON_DATE).orElseThrow().couponId();
				selected.countDown();
				await(release);
				return couponId;
			}));
			assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();

			final Future<Integer> inaccessibleUpdate = executor.submit(() -> transaction().execute(
				status -> touchCoupon(inaccessibleCouponId)));
			final Future<Integer> laterUpdate = executor.submit(() -> transaction().execute(
				status -> touchCoupon(laterCouponId)));
			assertThat(inaccessibleUpdate.get(2, TimeUnit.SECONDS)).isOne();
			assertThat(laterUpdate.get(2, TimeUnit.SECONDS)).isOne();

			final Future<Integer> selectedUpdate = executor.submit(() -> transaction().execute(
				status -> touchCoupon(selectedCouponId)));
			assertThatThrownBy(() -> selectedUpdate.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			release.countDown();
			assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo(selectedCouponId);
			assertThat(selectedUpdate.get(5, TimeUnit.SECONDS)).isOne();
		}
		finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private int touchCoupon(long couponId) {
		return jdbcTemplate.update("""
			UPDATE coupons
			SET updated_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", couponId);
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Coupon 잠금 해제 신호가 도착하지 않았습니다.");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Coupon 잠금 검증이 중단되었습니다.", exception);
		}
	}

	private long insertMember() {
		return insertMember("family-lock-member");
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '잠금 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertGroup() {
		jdbcTemplate.update("INSERT INTO family_groups (name) VALUES ('잠금 가족')");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id)
			VALUES (?, ?)
			""", groupId, memberId);
	}

	private long insertCoupon(long memberId, LocalDateTime expiresAt, LocalDateTime createdAt) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by, created_at, updated_at
			) VALUES (?, 'general', 10, 10, 0, ?, ?, 'active',
				'family-lock-admin', ?, ?)
			""", memberId, expiresAt.minusMonths(3), expiresAt, createdAt, createdAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void deleteTestData() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM family_group_audit_logs");
		jdbcTemplate.update("DELETE FROM family_memberships");
		jdbcTemplate.update("DELETE FROM family_groups");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'family-lock-%'");
	}
}
