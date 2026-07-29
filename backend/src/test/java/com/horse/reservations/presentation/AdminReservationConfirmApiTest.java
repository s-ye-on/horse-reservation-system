package com.horse.reservations.presentation;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservationConfirmApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant CONFIRMED_INSTANT = Instant.parse("2026-07-14T01:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_확정_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDate();
		when(clock.instant()).thenReturn(CONFIRMED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_쿠폰_승인대기_예약을_확정하고_점유_이력을_남긴다() throws Exception {
		final Long memberId = insertMember("confirm-coupon-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.status").value("confirmed"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.couponId").value(couponId))
			.andExpect(jsonPath("$.adminConfirmedAt").value("2026-07-14T10:00:00+09:00"));

		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(usageActionCount(reservationId, "confirmed")).isEqualTo(1);
		assertThat(confirmedActor(reservationId)).isEqualTo("admin");
	}

	@Test
	void 관리자는_마감_전_입금대기_예약을_쿠폰_부수효과_없이_확정한다() throws Exception {
		final Long memberId = insertMember("confirm-payment-member");
		final Long reservationId = insertPaymentReservation(memberId, "2026-07-14 10:01:00");

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("confirmed"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.couponId").doesNotExist())
			.andExpect(jsonPath("$.adminConfirmedAt").value("2026-07-14T10:00:00+09:00"));

		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(totalUsageLogCount()).isZero();
	}

	@Test
	void 입금_마감_시각에_도달한_예약은_확정하지_않는다() throws Exception {
		final Long memberId = insertMember("expired-payment-member");
		final Long reservationId = insertPaymentReservation(memberId, "2026-07-14 10:00:00");

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_PAYMENT_EXPIRED"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_payment");
		assertThat(adminConfirmedAt(reservationId)).isNull();
	}

	@Test
	void 수업_시작_시각에_도달한_승인대기_예약은_확정하지_않는다() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
		final Long memberId = insertMember("started-confirm-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_admin_approval");
		assertThat(adminConfirmedAt(reservationId)).isNull();
	}

	@Test
	void 중복_확정_요청은_예약과_쿠폰_감사를_한_번만_반영한다() throws Exception {
		final Long memberId = insertMember("idempotent-confirm-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentConfirmations(reservationId);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(usageActionCount(reservationId, "confirmed")).isEqualTo(1);
		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 허용되지_않은_상태와_없는_예약은_확정할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-confirm-member");
		final Long rejectedReservationId = insertRejectedReservation(memberId);

		mockMvc.perform(post(endpoint(rejectedReservationId)).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
		mockMvc.perform(post(endpoint(999999L)).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void 관리자_외_사용자는_예약을_확정할_수_없다() throws Exception {
		mockMvc.perform(post(endpoint(1L)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(endpoint(1L)).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	private List<Integer> concurrentConfirmations(Long reservationId) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return confirm(reservationId);
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

	private int confirm(Long reservationId) throws Exception {
		return mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
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

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '확정 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 1, 'confirm-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '09:00:00',
				'pending_admin_approval', 'coupon', ?, '2026-07-14 09:00:00')
			""", memberId, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPaymentReservation(Long memberId, String paymentDueAt) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '10:00:00',
				'pending_payment', 'single_payment', ?, '2026-07-14 09:00:00')
			""", memberId, paymentDueAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertRejectedReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, rejected_at, rejected_by, rejection_reason
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '11:00:00',
				'rejected', 'single_payment', '2026-07-14 11:00:00', '2026-07-14 09:00:00',
				'2026-07-14 09:30:00', 'reject-admin', '승인하지 않음')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, 'held', 1, '2026-07-14 09:00:00', 'member')
			""", couponId, reservationId, memberId);
	}

	private String endpoint(Long reservationId) {
		return "/api/admin/reservations/%d/confirm".formatted(reservationId);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private String adminConfirmedAt(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT DATE_FORMAT(admin_confirmed_at, '%Y-%m-%d %H:%i:%s') FROM reservations WHERE id = ?",
			String.class,
			reservationId);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?", Integer.class, couponId);
	}

	private int usageActionCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?",
			Integer.class,
			reservationId,
			action);
	}

	private String confirmedActor(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT actor_type FROM coupon_usage_logs WHERE reservation_id = ? AND action = 'confirmed'",
			String.class,
			reservationId);
	}

	private int totalUsageLogCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
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

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES ('2026-08-01', 'NORMAL', 1)
			""");
	}
}
