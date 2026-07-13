package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CouponHoldConcurrencyIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 10);
	private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 14, 10, 0);

	@Autowired
	CouponHoldService service;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM members");
	}

	@Test
	void 점유와_해제는_예약별로_한_번만_반영한다() {
		final TestData data = createTestData("hold-idempotent-member", 2);
		final Long reservationId = data.reservationIds().getFirst();

		final CouponHoldResult firstHold = hold(data, reservationId);
		final CouponHoldResult duplicateHold = hold(data, reservationId);
		final boolean firstRelease = service.release(
			reservationId, OCCURRED_AT.plusMinutes(1), CouponActorType.MEMBER);
		final boolean duplicateRelease = service.release(
			reservationId, OCCURRED_AT.plusMinutes(2), CouponActorType.MEMBER);

		assertThat(firstHold.changed()).isTrue();
		assertThat(duplicateHold.changed()).isFalse();
		assertThat(firstRelease).isTrue();
		assertThat(duplicateRelease).isFalse();
		assertThat(heldCount(data.couponId())).isZero();
		assertThat(logCount(reservationId, "held")).isEqualTo(1);
		assertThat(logCount(reservationId, "released")).isEqualTo(1);
	}

	@Test
	void 사용_가능_횟수_한_회의_병렬_점유는_한_요청만_성공한다() throws Exception {
		final TestData data = createTestData("hold-concurrency-member", 1);
		final CountDownLatch startSignal = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final List<Future<Boolean>> results = data.reservationIds().stream()
				.map(reservationId -> executor.submit(() -> attemptHold(startSignal, data, reservationId)))
				.toList();
			startSignal.countDown();

			final long successCount = results.stream()
				.map(this::getResult)
				.filter(Boolean::booleanValue)
				.count();

			assertThat(successCount).isEqualTo(1);
			assertThat(heldCount(data.couponId())).isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM coupon_usage_logs WHERE action = 'held'", Integer.class)).isEqualTo(1);
		}
		finally {
			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void 수업일에_만료된_쿠폰은_점유하지_않는다() {
		final TestData data = createTestData("hold-expired-member", 1);
		jdbcTemplate.update("""
			UPDATE coupons
			SET first_used_at = '2026-05-01 09:00:00', expires_at = '2026-08-09 09:00:00'
			WHERE id = ?
			""", data.couponId());

		assertThatThrownBy(() -> hold(data, data.reservationIds().getFirst()))
			.isInstanceOfSatisfying(
				CouponException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.COUPON_EXPIRED_FOR_LESSON.code()));
		assertThat(heldCount(data.couponId())).isZero();
		assertThat(logCount(data.reservationIds().getFirst(), "held")).isZero();
	}

	private boolean attemptHold(
		CountDownLatch startSignal,
		TestData data,
		Long reservationId
	) throws InterruptedException {
		startSignal.await();
		try {
			hold(data, reservationId);
			return true;
		}
		catch (CouponException exception) {
			assertThat(exception.code()).isEqualTo(ExceptionCode.COUPON_HOLD_NOT_AVAILABLE.code());
			return false;
		}
	}

	private boolean getResult(Future<Boolean> result) {
		try {
			return result.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError("병렬 쿠폰 점유 결과를 확인할 수 없습니다.", exception);
		}
	}

	private CouponHoldResult hold(TestData data, Long reservationId) {
		return service.hold(
			data.couponId(),
			reservationId,
			data.memberId(),
			LESSON_DATE,
			OCCURRED_AT,
			CouponActorType.MEMBER);
	}

	private TestData createTestData(String authSubject, int remainingCount) {
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, remainingCount);
		final List<Long> reservationIds = Stream.of("09:00:00", "10:00:00")
			.map(startTime -> insertReservation(memberId, couponId, startTime))
			.toList();
		return new TestData(memberId, couponId, reservationIds);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '점유 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private Long insertCoupon(Long memberId, int remainingCount) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, created_by
			) VALUES (?, 'general', 10, ?, 'hold-admin')
			""", memberId, remainingCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId, Long couponId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'pending_admin_approval', 'coupon', ?, ?)
			""", memberId, LESSON_DATE, startTime, couponId, OCCURRED_AT);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private int heldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?", Integer.class, couponId);
	}

	private int logCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private record TestData(Long memberId, Long couponId, List<Long> reservationIds) {
	}
}
