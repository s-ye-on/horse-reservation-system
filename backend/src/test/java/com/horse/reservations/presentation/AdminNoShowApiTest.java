package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
class AdminNoShowApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant PROCESSED_INSTANT = Instant.parse("2026-08-01T01:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_노쇼_처리_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(PROCESSED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_쿠폰_예약을_노쇼로_차감하고_감사_이력을_남긴다() throws Exception {
		final Long memberId = insertMember("deduct-no-show-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertConfirmedReservation(memberId, couponId, "coupon");
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("deduct", "연락 없이 미방문")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.status").value("no_show"))
			.andExpect(jsonPath("$.couponAction").value("deduct"))
			.andExpect(jsonPath("$.adminMemo").value("연락 없이 미방문"));

		assertThat(reservationValues(reservationId)).containsExactly("no_show", "deduct", "연락 없이 미방문");
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(couponUsageCount(reservationId, "deducted")).isEqualTo(1);
		assertThat(couponDatesAreNull(couponId)).isTrue();
		assertNoShowAudit(reservationId, "no-show-admin", "deduct", "연락 없이 미방문");
	}

	@Test
	void 수업_시작_전에는_노쇼_처리를_거부한다() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2026-08-01T00:00:00Z").minusNanos(1));
		final Long memberId = insertMember("early-no-show-member");
		final Long reservationId = insertConfirmedReservation(memberId, null, "single_payment");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("early-no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "수업 시작 전 노쇼")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_NOT_STARTED"));

		assertThat(reservationValues(reservationId).getFirst()).isEqualTo("confirmed");
		assertThat(changeLogCount()).isZero();
	}

	@Test
	void 관리자는_예외_노쇼의_쿠폰_점유를_반환한다() throws Exception {
		final Long memberId = insertMember("return-no-show-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertConfirmedReservation(memberId, couponId, "coupon");
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("return-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("return", "질병 사유 예외")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("no_show"))
			.andExpect(jsonPath("$.couponAction").value("return"));

		assertThat(couponCounts(couponId)).containsExactly(10, 0);
		assertThat(couponUsageCount(reservationId, "released")).isEqualTo(1);
		assertThat(couponUsageCount(reservationId, "deducted")).isZero();
		assertNoShowAudit(reservationId, "return-admin", "return", "질병 사유 예외");
	}

	@Test
	void 일회_결제_예약은_쿠폰_처리_없이_노쇼로_기록한다() throws Exception {
		final Long memberId = insertMember("single-payment-no-show-member");
		final Long reservationId = insertConfirmedReservation(memberId, null, "single_payment");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("payment-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "1회 결제 미방문")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("no_show"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.couponAction").value("none"));

		assertThat(totalCouponUsageCount()).isZero();
		assertNoShowAudit(reservationId, "payment-admin", "none", "1회 결제 미방문");
	}

	@Test
	void 예약_유형과_맞지_않는_쿠폰_처리는_거부한다() throws Exception {
		final Long couponMemberId = insertMember("invalid-coupon-no-show-member");
		final Long couponId = insertCoupon(couponMemberId);
		final Long couponReservationId = insertConfirmedReservation(couponMemberId, couponId, "coupon");
		insertHeldAndConfirmedLogs(couponMemberId, couponId, couponReservationId);
		final Long paymentMemberId = insertMember("invalid-payment-no-show-member");
		final Long paymentReservationId = insertConfirmedReservation(paymentMemberId, null, "single_payment");

		assertInvalidCouponAction(couponReservationId, "none");
		assertInvalidCouponAction(paymentReservationId, "deduct");
		assertInvalidCouponAction(paymentReservationId, "return");
		assertInvalidCouponAction(paymentReservationId, "unknown");

		assertThat(reservationValues(couponReservationId).getFirst()).isEqualTo("confirmed");
		assertThat(reservationValues(paymentReservationId).getFirst()).isEqualTo("confirmed");
		assertThat(couponCounts(couponId)).containsExactly(10, 1);
		assertThat(changeLogCount()).isZero();
	}

	@Test
	void 확정_예약과_유효한_관리자_메모만_노쇼_처리한다() throws Exception {
		final Long memberId = insertMember("invalid-state-no-show-member");
		final Long pendingReservationId = insertPendingPaymentReservation(memberId);

		mockMvc.perform(post(endpoint(pendingReservationId))
				.with(adminJwt("no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "아직 미확정")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
		mockMvc.perform(post(endpoint(pendingReservationId))
				.with(adminJwt("no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", " ")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(endpoint(pendingReservationId))
				.with(adminJwt("no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "가".repeat(501))))
			.andExpect(status().isBadRequest());

		assertThat(reservationValues(pendingReservationId).getFirst()).isEqualTo("pending_payment");
	}

	@Test
	void 동일한_노쇼_요청이_경쟁해도_쿠폰과_감사_이력을_한_번만_반영한다() throws Exception {
		final Long memberId = insertMember("concurrent-no-show-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertConfirmedReservation(memberId, couponId, "coupon");
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentNoShows(reservationId);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(couponUsageCount(reservationId, "deducted")).isEqualTo(1);
		assertThat(changeLogCount()).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 관리자만_예약을_노쇼로_처리할_수_있다() throws Exception {
		mockMvc.perform(post(endpoint(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "미인증")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(endpoint(1L))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("none", "회원 요청")))
			.andExpect(status().isForbidden());
	}

	private void assertInvalidCouponAction(Long reservationId, String couponAction) throws Exception {
		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("invalid-action-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(couponAction, "허용되지 않은 처리")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_COUPON_ACTION"));
	}

	private List<Integer> concurrentNoShows(Long reservationId) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return noShow(reservationId);
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

	private int noShow(Long reservationId) throws Exception {
		return mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("concurrent-no-show-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("deduct", "동시 노쇼 처리")))
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
			VALUES (?, '노쇼 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'no-show-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(Long memberId, Long couponId, String paymentSource) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, '09:00:00', 'confirmed', ?, ?, ?, ?, ?)
			""",
			memberId,
			LESSON_DATE,
			paymentSource,
			couponId,
			paymentSource.equals("single_payment") ? "2026-07-15 12:00:00" : null,
			"2026-07-15 09:00:00",
			"2026-07-15 10:00:00");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPendingPaymentReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '10:00:00', 'pending_payment', 'single_payment', ?, ?)
			""", memberId, LESSON_DATE, "2026-07-15 12:00:00", "2026-07-15 09:00:00");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldAndConfirmedLogs(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, 'held', 1, ?, 'member')
			""", couponId, reservationId, memberId, memberId, "2026-07-15 09:00:00");
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, 'confirmed', 0, ?, 'admin')
			""", couponId, reservationId, memberId, memberId, "2026-07-15 10:00:00");
	}

	private List<String> reservationValues(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT status, coupon_action, admin_memo FROM reservations WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getString("status"),
			String.valueOf(resultSet.getString("coupon_action")),
			String.valueOf(resultSet.getString("admin_memo"))), reservationId);
	}

	private List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT remaining_count, held_count FROM coupons WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getInt("remaining_count"), resultSet.getInt("held_count")), couponId);
	}

	private boolean couponDatesAreNull(Long couponId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
			SELECT first_used_at IS NULL AND expires_at IS NULL FROM coupons WHERE id = ?
			""", Boolean.class, couponId));
	}

	private int couponUsageCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int totalCouponUsageCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
	}

	private void assertNoShowAudit(
		Long reservationId,
		String actorSubject,
		String couponAction,
		String memo
	) {
		final List<String> values = jdbcTemplate.queryForObject("""
			SELECT actor_auth_subject, actor_type, from_status, to_status, change_type,
			       coupon_action, memo
			FROM reservation_change_logs WHERE reservation_id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getString("actor_auth_subject"),
			resultSet.getString("actor_type"),
			resultSet.getString("from_status"),
			resultSet.getString("to_status"),
			resultSet.getString("change_type"),
			resultSet.getString("coupon_action"),
			resultSet.getString("memo")), reservationId);
		assertThat(values).containsExactly(
			actorSubject, "admin", "confirmed", "no_show", "no_show_processed", couponAction, memo);
	}

	private int changeLogCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation_change_logs", Integer.class);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private String endpoint(Long reservationId) {
		return "/api/admin/reservations/" + reservationId + "/no-show";
	}

	private String request(String couponAction, String memo) {
		return """
			{
			  "couponAction": "%s",
			  "memo": "%s"
			}
			""".formatted(couponAction, memo);
	}

	private RequestPostProcessor adminJwt(String subject) {
		return jwt().jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().jwt(token -> token.subject("member-subject"))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
