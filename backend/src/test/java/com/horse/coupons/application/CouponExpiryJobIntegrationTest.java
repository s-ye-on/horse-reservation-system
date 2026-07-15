package com.horse.coupons.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.horse.TestcontainersConfiguration;
import com.horse.coupons.presentation.CouponExpiryScheduler;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CouponExpiryJobIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant EXECUTED_INSTANT = Instant.parse("2026-07-16T01:00:00Z");

	@Autowired
	CouponExpiryService expiryService;

	@Autowired
	CouponExpiryScheduler scheduler;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_만료_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(EXECUTED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 스케줄러는_만료일이_지난_사용_가능분만_소멸하고_한_번만_기록한다() {
		final Long memberId = insertMember("coupon-expiry-member");
		final Long expiredCouponId = insertCoupon(memberId, 9, 2, "2026-07-15 00:00:00");
		final Long boundaryCouponId = insertCoupon(memberId, 9, 0, "2026-07-16 00:00:00");

		scheduler.expireCoupons();
		scheduler.expireCoupons();

		assertThat(couponState(expiredCouponId)).isEqualTo("expired:2:2");
		assertThat(couponState(boundaryCouponId)).isEqualTo("active:9:0");
		assertThat(expiryLog(expiredCouponId)).containsExactly("expired:system:-7:null");
		assertThat(expiryLogCount()).isEqualTo(1);
	}

	@Test
	void 동시에_만료를_실행해도_한_트랜잭션만_상태와_로그를_변경한다() throws Exception {
		final Long memberId = insertMember("coupon-expiry-concurrency-member");
		final Long couponId = insertCoupon(memberId, 10, 0, "2026-07-15 00:00:00");

		final List<CouponExpiryResult> results = concurrentExpiryResults();

		assertThat(results).hasSize(2);
		assertThat(results.stream().mapToInt(CouponExpiryResult::expiredCouponCount).sum()).isEqualTo(1);
		assertThat(results.stream().mapToInt(CouponExpiryResult::expiredAvailableCount).sum()).isEqualTo(10);
		assertThat(couponState(couponId)).isEqualTo("expired:0:0");
		assertThat(expiryLogCount()).isEqualTo(1);
	}

	private List<CouponExpiryResult> concurrentExpiryResults() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<CouponExpiryResult>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return expiryService.expireDueCoupons();
				}))
				.toList();
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return futures.stream().map(this::getResult).toList();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private CouponExpiryResult getResult(Future<CouponExpiryResult> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError("병렬 쿠폰 만료 결과를 확인할 수 없습니다.", exception);
		}
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '만료 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, int remainingCount, int heldCount, String expiresAt) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by
			) VALUES (?, 'general', 10, ?, ?, '2026-04-15 00:00:00', ?, 'active', 'expiry-admin')
			""", memberId, remainingCount, heldCount, expiresAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String couponState(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(status, ':', remaining_count, ':', held_count)
			FROM coupons WHERE id = ?
			""", String.class, couponId);
	}

	private List<String> expiryLog(Long couponId) {
		return jdbcTemplate.queryForList("""
			SELECT CONCAT(action, ':', actor_type, ':', count_delta, ':',
				IF(reservation_id IS NULL, 'null', reservation_id))
			FROM coupon_usage_logs
			WHERE coupon_id = ? AND action = 'expired'
			ORDER BY id
			""", String.class, couponId);
	}

	private int expiryLogCount() {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE action = 'expired'", Integer.class);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
