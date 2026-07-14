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
import org.springframework.http.MediaType;
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
class AdminReservationRejectApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant REJECTED_INSTANT = Instant.parse("2026-07-14T01:00:00Z");
	private static final String REASON = "관리자 승인 기준을 충족하지 못했습니다.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_반려_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(REJECTED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_쿠폰_승인대기_예약을_반려하고_점유를_해제한다() throws Exception {
		final Long memberId = insertMember("reject-coupon-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(rejectRequest(reservationId, REASON).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.status").value("rejected"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.couponId").value(couponId))
			.andExpect(jsonPath("$.rejectedAt").value("2026-07-14T10:00:00"))
			.andExpect(jsonPath("$.rejectedBy").value("reject-admin"))
			.andExpect(jsonPath("$.rejectionReason").value(REASON));

		assertThat(reservationStatus(reservationId)).isEqualTo("rejected");
		assertThat(occupyingReservationCount()).isZero();
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(usageActionCount(reservationId, "released")).isEqualTo(1);
		assertThat(releasedActor(reservationId)).isEqualTo("admin");
	}

	@Test
	void 관리자는_입금대기_예약을_쿠폰_부수효과_없이_반려한다() throws Exception {
		final Long memberId = insertMember("reject-payment-member");
		final Long reservationId = insertPaymentReservation(memberId);

		mockMvc.perform(rejectRequest(reservationId, REASON).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("rejected"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.couponId").doesNotExist());

		assertThat(occupyingReservationCount()).isZero();
		assertThat(totalUsageLogCount()).isZero();
	}

	@Test
	void 중복_반려_요청은_예약과_쿠폰_반환을_한_번만_반영한다() throws Exception {
		final Long memberId = insertMember("idempotent-reject-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentRejections(reservationId);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(reservationStatus(reservationId)).isEqualTo("rejected");
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(usageActionCount(reservationId, "released")).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 확정된_예약과_없는_예약은_반려할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-reject-member");
		final Long reservationId = insertConfirmedReservation(memberId);

		mockMvc.perform(rejectRequest(reservationId, REASON).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
		mockMvc.perform(rejectRequest(999999L, REASON).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void 반려_사유는_필수이며_오백자를_넘을_수_없다() throws Exception {
		mockMvc.perform(rejectRequest(1L, " ").with(adminJwt()))
			.andExpect(status().isBadRequest());
		mockMvc.perform(rejectRequest(1L, "가".repeat(501)).with(adminJwt()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 관리자_외_사용자는_예약을_반려할_수_없다() throws Exception {
		mockMvc.perform(rejectRequest(1L, REASON))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(rejectRequest(1L, REASON).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	private List<Integer> concurrentRejections(Long reservationId) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return reject(reservationId);
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

	private int reject(Long reservationId) throws Exception {
		return mockMvc.perform(rejectRequest(reservationId, REASON).with(adminJwt()))
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

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rejectRequest(
		Long reservationId,
		String reason
	) {
		return post("/api/admin/reservations/{reservationId}/reject", reservationId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"%s\"}".formatted(reason));
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("reject-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '반려 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 1, 'reject-test-admin')
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

	private Long insertPaymentReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '10:00:00',
				'pending_payment', 'single_payment', '2026-07-14 12:00:00', '2026-07-14 09:00:00')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '11:00:00',
				'confirmed', 'single_payment', '2026-07-14 12:00:00',
				'2026-07-14 09:00:00', '2026-07-14 09:30:00')
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

	private String releasedActor(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT actor_type FROM coupon_usage_logs WHERE reservation_id = ? AND action = 'released'",
			String.class,
			reservationId);
	}

	private int totalUsageLogCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
