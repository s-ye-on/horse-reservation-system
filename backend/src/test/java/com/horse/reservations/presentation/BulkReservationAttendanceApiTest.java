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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
class BulkReservationAttendanceApiTest {

	private static final String ENDPOINT = "/api/admin/reservations/complete-bulk";
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
	void 데이터베이스와_처리_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(PROCESSED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_같은_시간대의_출석과_노쇼를_한_요청으로_처리한다() throws Exception {
		final Long completedMemberId = insertMember("bulk-completed-member");
		final Long completedCouponId = insertCoupon(completedMemberId);
		final Long completedReservationId = insertReservation(
			completedMemberId, completedCouponId, "confirmed", "coupon", "09:00:00");
		insertCouponHoldLogs(completedMemberId, completedCouponId, completedReservationId);
		final Long noShowMemberId = insertMember("bulk-no-show-member");
		final Long noShowCouponId = insertCoupon(noShowMemberId);
		final Long noShowReservationId = insertReservation(
			noShowMemberId, noShowCouponId, "confirmed", "coupon", "09:00:00");
		insertCouponHoldLogs(noShowMemberId, noShowCouponId, noShowReservationId);

		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mixedRequest(completedReservationId, noShowReservationId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestedCount").value(2))
			.andExpect(jsonPath("$.succeededCount").value(2))
			.andExpect(jsonPath("$.failedCount").value(0))
			.andExpect(jsonPath("$.items[0].status").value("completed"))
			.andExpect(jsonPath("$.items[1].status").value("no_show"));

		assertThat(reservationStatus(completedReservationId)).isEqualTo("completed");
		assertThat(reservationStatus(noShowReservationId)).isEqualTo("no_show");
		assertThat(memberRideCount(completedMemberId)).isEqualTo(1);
		assertThat(couponCounts(completedCouponId)).containsExactly(9, 0);
		assertThat(couponCounts(noShowCouponId)).containsExactly(9, 0);
		assertThat(usageActionCount(completedReservationId, "used")).isEqualTo(1);
		assertThat(usageActionCount(noShowReservationId, "deducted")).isEqualTo(1);
		assertThat(noShowLogCount(noShowReservationId)).isEqualTo(1);
	}

	@Test
	void 한_예약이_실패해도_다른_예약의_성공은_커밋한다() throws Exception {
		final Long successMemberId = insertMember("bulk-partial-success-member");
		final Long successReservationId = insertReservation(
			successMemberId, null, "confirmed", "single_payment", "09:00:00");
		final Long failedMemberId = insertMember("bulk-partial-failed-member");
		final Long failedReservationId = insertReservation(
			failedMemberId, null, "pending_payment", "single_payment", "09:00:00");

		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(completeRequest(successReservationId, failedReservationId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.succeededCount").value(1))
			.andExpect(jsonPath("$.failedCount").value(1))
			.andExpect(jsonPath("$.items[0].status").value("completed"))
			.andExpect(jsonPath("$.items[1].success").value(false))
			.andExpect(jsonPath("$.items[1].errorCode").value("RESERVATION_INVALID_STATUS"));

		assertThat(reservationStatus(successReservationId)).isEqualTo("completed");
		assertThat(reservationStatus(failedReservationId)).isEqualTo("pending_payment");
		assertThat(memberRideCount(successMemberId)).isEqualTo(1);
		assertThat(memberRideCount(failedMemberId)).isZero();
	}

	@Test
	void 요청한_시간대와_다른_예약은_해당_항목만_실패한다() throws Exception {
		final Long memberId = insertMember("bulk-mismatch-member");
		final Long reservationId = insertReservation(
			memberId, null, "confirmed", "single_payment", "10:00:00");

		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(singleCompleteRequest(reservationId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.succeededCount").value(0))
			.andExpect(jsonPath("$.failedCount").value(1))
			.andExpect(jsonPath("$.items[0].errorCode").value("RESERVATION_INVALID_STATUS"));

		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(memberRideCount(memberId)).isZero();
	}

	@Test
	void 동일한_일괄_요청을_반복해도_부수_효과는_한_번만_반영한다() throws Exception {
		final Long completedMemberId = insertMember("bulk-repeat-completed-member");
		final Long completedCouponId = insertCoupon(completedMemberId);
		final Long completedReservationId = insertReservation(
			completedMemberId, completedCouponId, "confirmed", "coupon", "09:00:00");
		insertCouponHoldLogs(completedMemberId, completedCouponId, completedReservationId);
		final Long noShowMemberId = insertMember("bulk-repeat-no-show-member");
		final Long noShowCouponId = insertCoupon(noShowMemberId);
		final Long noShowReservationId = insertReservation(
			noShowMemberId, noShowCouponId, "confirmed", "coupon", "09:00:00");
		insertCouponHoldLogs(noShowMemberId, noShowCouponId, noShowReservationId);
		final String request = mixedRequest(completedReservationId, noShowReservationId);

		for (int index = 0; index < 2; index++) {
			mockMvc.perform(post(ENDPOINT)
					.with(adminJwt())
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.succeededCount").value(2));
		}

		assertThat(memberRideCount(completedMemberId)).isEqualTo(1);
		assertThat(couponCounts(completedCouponId)).containsExactly(9, 0);
		assertThat(couponCounts(noShowCouponId)).containsExactly(9, 0);
		assertThat(usageActionCount(completedReservationId, "used")).isEqualTo(1);
		assertThat(usageActionCount(noShowReservationId, "deducted")).isEqualTo(1);
		assertThat(noShowLogCount(noShowReservationId)).isEqualTo(1);
	}

	@Test
	void 요청은_최대_여덟_항목이며_관리자만_실행할_수_있다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(overCapacityRequest()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content(singleCompleteRequest(1L)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(singleCompleteRequest(1L)))
			.andExpect(status().isForbidden());
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '일괄 처리 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'bulk-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(
		Long memberId,
		Long couponId,
		String reservationStatus,
		String paymentSource,
		String startTime
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, ?, ?, ?, '2026-07-17 09:00:00', ?)
			""",
			memberId,
			LESSON_DATE,
			startTime,
			reservationStatus,
			paymentSource,
			couponId,
			"single_payment".equals(paymentSource) ? "2026-07-17 12:00:00" : null,
			"confirmed".equals(reservationStatus) ? "2026-07-17 09:30:00" : null);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertCouponHoldLogs(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES
				(?, ?, ?, ?, 'held', 1, '2026-07-17 09:00:00', 'member'),
				(?, ?, ?, ?, 'confirmed', 0, '2026-07-17 09:30:00', 'admin')
			""",
			couponId, reservationId, memberId, memberId,
			couponId, reservationId, memberId, memberId);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private int memberRideCount(Long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?", Integer.class, memberId);
	}

	private java.util.List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT remaining_count, held_count FROM coupons WHERE id = ?
			""", (resultSet, rowNumber) -> java.util.List.of(
			resultSet.getInt("remaining_count"), resultSet.getInt("held_count")), couponId);
	}

	private int usageActionCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int noShowLogCount(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM reservation_change_logs
			WHERE reservation_id = ? AND change_type = 'no_show_processed'
			""", Integer.class, reservationId);
	}

	private String mixedRequest(Long completedReservationId, Long noShowReservationId) {
		return """
			{
			  "lessonDate": "2026-08-01",
			  "startTime": "09:00:00",
			  "items": [
			    {"reservationId": %d, "action": "complete"},
			    {
			      "reservationId": %d,
			      "action": "no_show",
			      "couponAction": "deduct",
			      "memo": "일괄 노쇼 처리"
			    }
			  ]
			}
			""".formatted(completedReservationId, noShowReservationId);
	}

	private String completeRequest(Long firstReservationId, Long secondReservationId) {
		return """
			{
			  "lessonDate": "2026-08-01",
			  "startTime": "09:00:00",
			  "items": [
			    {"reservationId": %d, "action": "complete"},
			    {"reservationId": %d, "action": "complete"}
			  ]
			}
			""".formatted(firstReservationId, secondReservationId);
	}

	private String singleCompleteRequest(Long reservationId) {
		return """
			{
			  "lessonDate": "2026-08-01",
			  "startTime": "09:00:00",
			  "items": [{"reservationId": %d, "action": "complete"}]
			}
			""".formatted(reservationId);
	}

	private String overCapacityRequest() {
		final String items = IntStream.rangeClosed(1, 9)
			.mapToObj(id -> "{\"reservationId\": %d, \"action\": \"complete\"}".formatted(id))
			.collect(Collectors.joining(","));
		return """
			{
			  "lessonDate": "2026-08-01",
			  "startTime": "09:00:00",
			  "items": [%s]
			}
			""".formatted(items);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().jwt(builder -> builder.subject("bulk-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().jwt(builder -> builder.subject("bulk-member"))
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
