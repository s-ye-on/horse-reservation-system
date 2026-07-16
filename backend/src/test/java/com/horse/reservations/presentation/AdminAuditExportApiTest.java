package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditExportApiTest {

	private static final String RESERVATION_EXPORT = "/api/admin/audit-logs/export";
	private static final String COUPON_EXPORT = "/api/admin/coupon-usage-logs/export";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		clearDatabase();
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 예약_감사_CSV는_필터와_최신순을_적용하고_민감정보를_제외한다() throws Exception {
		final Long memberId = insertMember("audit-member", "=위험회원", "010-1234-7788");
		final Long reservationId = insertReservation(memberId);
		final Long olderId = insertAuditLog(
			reservationId, "private-member-subject", "member", "schedule_changed", "이전 메모",
			"2026-07-15 09:00:00");
		final Long newerId = insertAuditLog(
			reservationId, "private-admin-subject", "admin", "schedule_changed", "최신 메모",
			"2026-07-16 09:00:00");

		final List<Integer> before = databaseCounts();
		final MvcResult result = mockMvc.perform(get(RESERVATION_EXPORT)
				.param("keyword", "7788")
				.param("reservationId", reservationId.toString())
				.param("occurredDateFrom", "2026-07-01")
				.param("occurredDateTo", "2026-07-31")
				.with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("reservation-audit.csv")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andReturn();

		final byte[] bytes = result.getResponse().getContentAsByteArray();
		final String csv = new String(bytes, StandardCharsets.UTF_8);
		assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
		assertThat(csv).contains("\"audit_log_id\"", "\"'=위험회원\"", "최신 메모", "이전 메모");
		assertThat(csv.indexOf(newerId.toString())).isLessThan(csv.indexOf(olderId.toString()));
		assertThat(csv).doesNotContain("010-1234-7788", "private-member-subject", "private-admin-subject");
		assertThat(databaseCounts()).isEqualTo(before);
	}

	@Test
	void 쿠폰_사용_CSV는_회원_쿠폰_예약_기간을_필터하고_현재값을_추론하지_않는다() throws Exception {
		final Long memberId = insertMember("coupon-member", "쿠폰회원", "010-9999-7788");
		final Long reservationId = insertReservation(memberId);
		final Long couponId = insertCoupon(memberId, "private-register-admin");
		insertCouponUsageLog(couponId, reservationId, memberId, "used", -1, "사용 완료", "2026-07-16 10:00:00");

		final MvcResult result = mockMvc.perform(get(COUPON_EXPORT)
				.param("keyword", "7788")
				.param("couponId", couponId.toString())
				.param("reservationId", reservationId.toString())
				.param("occurredDateFrom", "2026-07-16")
				.param("occurredDateTo", "2026-07-16")
				.with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("coupon-usage.csv")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andReturn();

		final String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(csv).contains(
			"\"usage_log_id\"",
			"\"coupon_type\"",
			"\"쿠폰회원\"",
			"\"general\"",
			"\"used\"",
			"\"'-1\"",
			"\"사용 완료\"");
		assertThat(csv).doesNotContain("010-9999-7788", "private-register-admin");
	}

	@Test
	void 빈_결과도_BOM과_헤더가_있는_CSV를_반환한다() throws Exception {
		final MvcResult reservation = mockMvc.perform(get(RESERVATION_EXPORT).with(adminJwt()))
			.andExpect(status().isOk())
			.andReturn();
		final MvcResult coupon = mockMvc.perform(get(COUPON_EXPORT).with(adminJwt()))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(new String(reservation.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
			.startsWith("\uFEFF\"occurred_at\",\"audit_log_id\"")
			.endsWith("\r\n");
		assertThat(new String(coupon.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
			.startsWith("\uFEFF\"occurred_at\",\"usage_log_id\"")
			.endsWith("\r\n");
	}

	@Test
	void 관리자만_두_감사_CSV를_다운로드할_수_있다() throws Exception {
		mockMvc.perform(get(RESERVATION_EXPORT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(RESERVATION_EXPORT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(COUPON_EXPORT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(COUPON_EXPORT).with(memberJwt())).andExpect(status().isForbidden());
	}

	@Test
	void 잘못된_다운로드_기간은_통일된_오류로_거부한다() throws Exception {
		mockMvc.perform(get(RESERVATION_EXPORT)
				.param("occurredDateFrom", "2026-07-31")
				.param("occurredDateTo", "2026-07-01")
				.with(adminJwt()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_AUDIT_DATE_RANGE"));
		mockMvc.perform(get(COUPON_EXPORT)
				.param("occurredDateFrom", "2026-07-31")
				.param("occurredDateTo", "2026-07-01")
				.with(adminJwt()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_INVALID_EXPORT_DATE_RANGE"));
	}

	private Long insertMember(String authSubject, String name, String phone) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, ?, FALSE)
			""", authSubject, name, phone);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', '2026-07-20', '09:00:00', 'confirmed', 'single_payment',
				'2026-07-15 11:00:00', '2026-07-15 09:00:00', '2026-07-15 09:30:00')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertAuditLog(
		Long reservationId,
		String actorAuthSubject,
		String actorType,
		String changeType,
		String memo,
		String createdAt
	) {
		jdbcTemplate.update("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo, created_at
			) VALUES (?, ?, ?, 'confirmed', 'confirmed', '2026-07-20', '09:00:00',
				'2026-07-20', '10:00:00', ?, 'none', ?, ?)
			""", reservationId, actorAuthSubject, actorType, changeType, memo, createdAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, String createdBy) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, free_change_used,
				status, created_by
			) VALUES (?, 'general', 10, 9, 0, FALSE, 'active', ?)
			""", memberId, createdBy);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertCouponUsageLog(
		Long couponId,
		Long reservationId,
		Long memberId,
		String action,
		int countDelta,
		String memo,
		String occurredAt
	) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
			) VALUES (?, ?, ?, ?, ?, ?, 'admin', ?)
			""", couponId, reservationId, memberId, action, countDelta, occurredAt, memo);
	}

	private List<Integer> databaseCounts() {
		return List.of(
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class),
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation_change_logs", Integer.class),
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupons", Integer.class),
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class));
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
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
