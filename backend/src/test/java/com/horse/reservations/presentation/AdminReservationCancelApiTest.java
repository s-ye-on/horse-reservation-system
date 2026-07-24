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
class AdminReservationCancelApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 3);
	private static final Instant AFTER_CUTOFF = Instant.parse("2026-08-02T12:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_관리자_취소_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDate();
		when(clock.instant()).thenReturn(AFTER_CUTOFF);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_권장값과_다른_허용된_쿠폰_차감을_선택하고_멱등하게_취소한다() throws Exception {
		final Long memberId = insertMember();
		final Long couponId = insertCoupon(memberId, 2);
		final Long reservationId = insertCouponReservation(memberId, couponId, "09:00:00");
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(get(previewEndpoint(reservationId))
				.param("responsibility", "stable")
				.with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timing").value("after_cutoff_weekday"))
			.andExpect(jsonPath("$.responsibility").value("stable"))
			.andExpect(jsonPath("$.couponAction").value("return"));

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post(cancelEndpoint(reservationId))
					.with(adminJwt())
					.contentType(MediaType.APPLICATION_JSON)
					.content(request("member", "deduct", "회원 책임 차감")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("cancelled"))
				.andExpect(jsonPath("$.responsibility").value("member"))
				.andExpect(jsonPath("$.couponAction").value("deduct"))
				.andExpect(jsonPath("$.changed").value(attempt == 0));
		}

		assertThat(couponCounts(couponId)).containsExactly(1, 0);
		assertThat(usageCount(reservationId, "deducted")).isEqualTo(1);
		assertAudit(reservationId, "member", "deduct", "회원 책임 차감");
	}

	@Test
	void 관리자는_마장_책임_쿠폰_예약을_반환한다() throws Exception {
		final Long memberId = insertMember();
		final Long couponId = insertCoupon(memberId, 1);
		final Long reservationId = insertCouponReservation(memberId, couponId, "10:00:00");
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("stable", "return", "우천으로 마장 취소")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("return"));

		assertThat(couponCounts(couponId)).containsExactly(1, 0);
		assertThat(usageCount(reservationId, "released")).isEqualTo(1);
		assertAudit(reservationId, "stable", "return", "우천으로 마장 취소");
	}

	@Test
	void 관리자는_일회_결제_예약을_none으로_취소한다() throws Exception {
		final Long reservationId = insertSinglePaymentReservation(insertMember());

		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("exception", "none", "입금 전 예외 취소")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.responsibility").value("exception"))
			.andExpect(jsonPath("$.couponAction").value("none"));

		assertThat(totalUsageCount()).isZero();
		assertAudit(reservationId, "exception", "none", "입금 전 예외 취소");
	}

	@Test
	void 수업_시작_시각에는_관리자_취소_preview와_실행을_모두_거부한다() throws Exception {
		when(clock.instant()).thenReturn(Instant.parse("2026-08-03T00:00:00Z"));
		final Long memberId = insertMember();
		final Long couponId = insertCoupon(memberId, 1);
		final Long reservationId = insertCouponReservation(memberId, couponId, "09:00:00");
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(get(previewEndpoint(reservationId))
				.param("responsibility", "stable")
				.with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("stable", "return", "수업 시작 후 취소")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_admin_approval");
		assertThat(couponCounts(couponId)).containsExactly(1, 1);
	}

	@Test
	void 관리자는_잘못된_쿠폰_처리와_빈_메모를_사용할_수_없고_회원은_접근할_수_없다() throws Exception {
		final Long memberId = insertMember();
		final Long couponId = insertCoupon(memberId, 1);
		final Long couponReservationId = insertCouponReservation(memberId, couponId, "11:00:00");
		insertHeldLog(memberId, couponId, couponReservationId);
		final Long singlePaymentReservationId = insertSinglePaymentReservation(memberId);

		assertInvalidRequest(couponReservationId, request("member", "none", "잘못된 처리"));
		assertInvalidRequest(singlePaymentReservationId, request("member", "deduct", "잘못된 처리"));
		assertInvalidRequest(couponReservationId, request("member", "return", " "));
		mockMvc.perform(get(previewEndpoint(couponReservationId))
				.param("responsibility", "unknown")
				.with(adminJwt()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("RESERVATION_INVALID_CANCELLATION_RESPONSIBILITY"));

		mockMvc.perform(post(cancelEndpoint(couponReservationId))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("member", "return", "권한 없는 처리")))
			.andExpect(status().isForbidden());
		assertThat(reservationStatus(couponReservationId)).isEqualTo("pending_admin_approval");
	}

	private void assertInvalidRequest(Long reservationId, String content) throws Exception {
		mockMvc.perform(post(cancelEndpoint(reservationId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(content))
			.andExpect(status().isBadRequest());
	}

	private Long insertMember() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES ('admin-cancel-member', '관리자 취소 회원', '010-1111-2222', FALSE)
			""");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, int remainingCount) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, ?, 1, 'active', 'admin-cancel-test')
			""", memberId, remainingCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'pending_admin_approval', 'coupon', ?,
				'2026-08-01 09:00:00')
			""", memberId, LESSON_DATE, startTime, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '12:00:00', 'pending_payment', 'single_payment',
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

	private int usageCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int totalUsageCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private void assertAudit(
		Long reservationId,
		String responsibility,
		String couponAction,
		String memo
	) {
		final List<String> reservationValues = jdbcTemplate.queryForObject("""
			SELECT cancellation_responsibility, coupon_action, admin_memo
			FROM reservations WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getString("cancellation_responsibility"),
			resultSet.getString("coupon_action"),
			resultSet.getString("admin_memo")), reservationId);
		assertThat(reservationValues).containsExactly(responsibility, couponAction, memo);
		final List<String> logValues = jdbcTemplate.queryForObject("""
			SELECT actor_auth_subject, actor_type, change_type, coupon_action, memo
			FROM reservation_change_logs WHERE reservation_id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getString("actor_auth_subject"),
			resultSet.getString("actor_type"),
			resultSet.getString("change_type"),
			resultSet.getString("coupon_action"),
			resultSet.getString("memo")), reservationId);
		assertThat(logValues).containsExactly(
			"admin-cancel-test", "admin", "reservation_cancelled", couponAction, memo);
	}

	private String previewEndpoint(Long reservationId) {
		return "/api/admin/reservations/" + reservationId + "/cancellation-preview";
	}

	private String cancelEndpoint(Long reservationId) {
		return "/api/admin/reservations/" + reservationId + "/cancel";
	}

	private String request(String responsibility, String couponAction, String memo) {
		return """
			{"responsibility":"%s","couponAction":"%s","memo":"%s"}
			""".formatted(responsibility, couponAction, memo);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().jwt(token -> token.subject("admin-cancel-test"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().jwt(token -> token.subject("member-cancel-test"))
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
