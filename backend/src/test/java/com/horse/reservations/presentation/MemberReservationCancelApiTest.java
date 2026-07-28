package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MemberReservationCancelApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 3);
	private static final Instant BEFORE_CUTOFF = Instant.parse("2026-08-02T11:59:59Z");
	private static final Instant AT_CUTOFF = Instant.parse("2026-08-02T12:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_취소_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDate();
		when(clock.instant()).thenReturn(BEFORE_CUTOFF);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 회원은_마감_전_쿠폰_예약의_예상_반환을_확인하고_취소한다() throws Exception {
		final String authSubject = "before-cancel-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(get(previewEndpoint(reservationId)).with(memberJwt(authSubject)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timing").value("before_cutoff"))
			.andExpect(jsonPath("$.responsibility").value("member"))
			.andExpect(jsonPath("$.couponAction").value("return"));

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post(cancelEndpoint(reservationId))
					.with(memberJwt(authSubject))
					.contentType(MediaType.APPLICATION_JSON)
					.content(request("개인 일정으로 취소")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("cancelled"))
				.andExpect(jsonPath("$.responsibility").value("member"))
				.andExpect(jsonPath("$.couponAction").value("return"))
				.andExpect(jsonPath("$.changed").value(attempt == 0));
		}

		assertThat(couponCounts(couponId)).containsExactly(10, 0);
		assertThat(couponUsageCount(reservationId, "released")).isEqualTo(1);
		assertCancellationAudit(
			reservationId,
			authSubject,
			"pending_admin_approval",
			"return",
			"개인 일정으로 취소");
	}

	@Test
	void 동일한_회원_취소가_경쟁해도_쿠폰과_감사_이력을_한_번만_반영한다() throws Exception {
		final String authSubject = "concurrent-cancel-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentCancellations(reservationId, authSubject);

		assertThat(statuses).containsExactly(200, 200);
		assertThat(reservationStatus(reservationId)).isEqualTo("cancelled");
		assertThat(couponCounts(couponId)).containsExactly(10, 0);
		assertThat(couponUsageCount(reservationId, "released")).isEqualTo(1);
		assertThat(cancellationAuditCount(reservationId)).isEqualTo(1);
	}

	@Test
	void 마감_후_승인대기_쿠폰_예약은_확정_로그가_없어도_점유를_차감한다() throws Exception {
		when(clock.instant()).thenReturn(AT_CUTOFF);
		final String authSubject = "after-cancel-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(get(previewEndpoint(reservationId)).with(memberJwt(authSubject)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timing").value("after_cutoff_weekday"))
			.andExpect(jsonPath("$.couponAction").value("deduct"));
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("마감 후 취소")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("deduct"));

		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(couponUsageCount(reservationId, "confirmed")).isZero();
		assertThat(couponUsageCount(reservationId, "deducted")).isEqualTo(1);
		assertCancellationAudit(
			reservationId,
			authSubject,
			"pending_admin_approval",
			"deduct",
			"마감 후 취소");
	}

	@Test
	void 일회_결제_예약은_마감_후에도_쿠폰_처리_없이_취소한다() throws Exception {
		when(clock.instant()).thenReturn(AT_CUTOFF);
		final String authSubject = "single-payment-cancel-member";
		final Long memberId = insertMember(authSubject);
		final Long reservationId = insertSinglePaymentReservation(memberId);

		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("입금 전 취소")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("cancelled"))
			.andExpect(jsonPath("$.couponAction").value("none"));

		assertThat(totalCouponUsageCount()).isZero();
		assertCancellationAudit(
			reservationId,
			authSubject,
			"pending_payment",
			"none",
			"입금 전 취소");
	}

	@Test
	void 수업_시작_시각에는_회원_취소_preview와_실행을_모두_거부한다() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2026-08-03T00:00:00Z"));
		final String authSubject = "started-member-cancel";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(get(previewEndpoint(reservationId)).with(memberJwt(authSubject)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("수업 시작 후 취소")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_admin_approval");
		assertThat(couponCounts(couponId)).containsExactly(10, 1);
	}

	@Test
	void 회원은_다른_회원_예약과_빈_사유와_종료된_예약을_취소할_수_없다() throws Exception {
		final Long ownerId = insertMember("cancel-owner");
		insertMember("cancel-other");
		final Long reservationId = insertSinglePaymentReservation(ownerId);

		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt("cancel-other"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("다른 회원 취소")))
			.andExpect(status().isNotFound());
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt("cancel-owner"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(" ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_CANCELLATION_REASON"));
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt("cancel-owner"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_CANCELLATION_REASON"));

		jdbcTemplate.update("UPDATE reservations SET status = 'payment_expired' WHERE id = ?", reservationId);
		mockMvc.perform(get(previewEndpoint(reservationId)).with(memberJwt("cancel-owner")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt("cancel-owner"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("종료 예약 취소")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '취소 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'cancel-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '09:00:00', 'pending_admin_approval',
				'coupon', ?, '2026-08-01 09:00:00')
			""", memberId, LESSON_DATE, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '10:00:00', 'pending_payment', 'single_payment',
				'2026-08-03 08:00:00', '2026-08-01 09:00:00')
			""", memberId, LESSON_DATE);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, 'held', 1, '2026-08-01 09:00:00', 'member')
			""", couponId, reservationId, memberId);
	}

	private List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT remaining_count, held_count FROM coupons WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getInt("remaining_count"),
			resultSet.getInt("held_count")), couponId);
	}

	private int couponUsageCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int totalCouponUsageCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private void assertCancellationAudit(
		Long reservationId,
		String actorSubject,
		String fromStatus,
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
			actorSubject,
			"member",
			fromStatus,
			"cancelled",
			"reservation_cancelled",
			couponAction,
			memo);
	}

	private List<Integer> concurrentCancellations(Long reservationId, String authSubject) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return cancel(reservationId, authSubject);
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

	private int cancel(Long reservationId, String authSubject) throws Exception {
		return mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("동시 취소")))
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

	private int cancellationAuditCount(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservation_change_logs
			WHERE reservation_id = ? AND change_type = 'reservation_cancelled'
			""", Integer.class, reservationId);
	}

	private String previewEndpoint(Long reservationId) {
		return "/api/me/reservations/" + reservationId + "/cancellation-preview";
	}

	private String cancelEndpoint(Long reservationId) {
		return "/api/me/reservations/" + reservationId + "/cancel";
	}

	private String request(String reason) {
		return """
			{"reason": "%s"}
			""".formatted(reason);
	}

	private RequestPostProcessor memberJwt(String subject) {
		return jwt().jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES (?, 'NORMAL', 1)
			""", LESSON_DATE);
	}
}
