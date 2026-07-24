package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.reservations.presentation.PendingPaymentExpiryScheduler;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PendingPaymentExpiryJobIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant EXECUTED_INSTANT = Instant.parse("2026-07-15T01:00:00Z");
	private static final String ENDPOINT = "/api/admin/jobs/expire-pending-payments";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PendingPaymentExpiryService expiryService;

	@Autowired
	PendingPaymentExpiryScheduler scheduler;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_만료_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDates();
		when(clock.instant()).thenReturn(EXECUTED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_마감_시각이_지난_입금대기만_수동으로_만료한다() throws Exception {
		final Long memberId = insertMember("manual-expiry-member");
		final Long beforeBoundaryId = insertPaymentReservation(
			memberId, "2026-07-15 09:59:59", "09:00:00");
		final Long boundaryId = insertPaymentReservation(
			memberId, "2026-07-15 10:00:00", "10:00:00");
		final Long futureId = insertPaymentReservation(
			memberId, "2026-07-15 10:00:01", "11:00:00");
		final Long couponPendingId = insertCouponPendingReservation(memberId);
		final Long confirmedId = insertConfirmedReservation(memberId);

		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiredCount").value(2))
			.andExpect(jsonPath("$.executedAt").value("2026-07-15T10:00:00"));

		assertThat(reservationStatus(beforeBoundaryId)).isEqualTo("payment_expired");
		assertThat(reservationStatus(boundaryId)).isEqualTo("payment_expired");
		assertThat(reservationStatus(futureId)).isEqualTo("pending_payment");
		assertThat(reservationStatus(couponPendingId)).isEqualTo("pending_admin_approval");
		assertThat(reservationStatus(confirmedId)).isEqualTo("confirmed");
		assertThat(occupyingReservationCount()).isEqualTo(3);
	}

	@Test
	void 스케줄러는_동일한_서비스로_만료하고_재실행해도_상태를_반복_변경하지_않는다() {
		final Long memberId = insertMember("scheduled-expiry-member");
		final Long reservationId = insertPaymentReservation(
			memberId, "2026-07-15 10:00:00", "09:00:00");

		scheduler.expirePendingPayments();
		scheduler.expirePendingPayments();

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
		assertThat(occupyingReservationCount()).isZero();
	}

	@Test
	void 입금_마감이_남아_있어도_수업_시작_시각이면_만료한다() throws Exception {
		final Long memberId = insertMember("lesson-start-expiry-member");
		final Long reservationId = insertPaymentReservation(
			memberId, "2026-07-15 11:00:00", "10:00:00");
		jdbcTemplate.update(
			"UPDATE reservations SET lesson_date = '2026-07-15' WHERE id = ?", reservationId);

		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiredCount").value(1));

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(occupyingReservationCount()).isZero();
	}

	@Test
	void 동시에_만료를_실행해도_한_요청만_예약을_변경한다() throws Exception {
		final Long memberId = insertMember("concurrent-expiry-member");
		final Long reservationId = insertPaymentReservation(
			memberId, "2026-07-15 10:00:00", "09:00:00");

		final List<Integer> expiredCounts = concurrentExpiryCounts();

		assertThat(expiredCounts).hasSize(2);
		assertThat(expiredCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 결제_마감_시각의_확정과_만료_경쟁에서는_만료만_성공한다() throws Exception {
		final Long memberId = insertMember("confirm-expiry-race-member");
		final Long reservationId = insertPaymentReservation(
			memberId, "2026-07-15 10:00:00", "09:00:00");

		final List<Integer> statuses = concurrentConfirmAndExpiry(reservationId);

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 관리자만_수동_만료_작업을_실행할_수_있다() throws Exception {
		mockMvc.perform(post(ENDPOINT))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk());
	}

	private List<Integer> concurrentExpiryCounts() throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return expiryService.expireDuePayments().expiredCount();
				}))
				.toList();
			ready.await();
			start.countDown();
			return futures.stream().map(this::getResult).toList();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private List<Integer> concurrentConfirmAndExpiry(Long reservationId) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final Future<Integer> confirm = executor.submit(() -> {
				ready.countDown();
				start.await();
				return responseStatus("/api/admin/reservations/%d/confirm".formatted(reservationId));
			});
			final Future<Integer> expiry = executor.submit(() -> {
				ready.countDown();
				start.await();
				return responseStatus(ENDPOINT);
			});
			ready.await();
			start.countDown();
			return List.of(getResult(confirm), getResult(expiry));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private int responseStatus(String endpoint) throws Exception {
		return mockMvc.perform(post(endpoint).with(adminJwt()))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private int getResult(Future<Integer> future) {
		try {
			return future.get();
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '만료 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPaymentReservation(Long memberId, String paymentDueAt, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', ?,
				'pending_payment', 'single_payment', ?, '2026-07-15 08:00:00')
			""", memberId, startTime, paymentDueAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponPendingReservation(Long memberId) {
		final Long couponId = insertCoupon(memberId);
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '12:00:00',
				'pending_admin_approval', 'coupon', ?, '2026-07-15 08:00:00')
			""", memberId, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '13:00:00',
				'confirmed', 'single_payment', '2026-07-15 09:00:00',
				'2026-07-15 07:00:00', '2026-07-15 08:00:00')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 1, 'expiry-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int occupyingReservationCount() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM reservations
			WHERE status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
			""", Integer.class);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void insertScheduleDates() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES
				('2026-07-15', 'NORMAL', 1),
				('2026-08-01', 'NORMAL', 1)
			""");
	}
}
