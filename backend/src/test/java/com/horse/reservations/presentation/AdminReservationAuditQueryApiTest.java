package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservationAuditQueryApiTest {

	private static final String ENDPOINT = "/api/admin/audit-logs";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		clearDatabase();
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 전체_이력은_최신_발생순과_이력_ID_역순으로_조회한다() throws Exception {
		final Long memberId = insertMember("audit-sort-member", "정렬 회원", "010-1111-2222");
		final Long reservationId = insertReservation(memberId);
		final Long olderId = insertAuditLog(
			reservationId,
			"member-subject",
			"member",
			"schedule_changed",
			"confirmed",
			"confirmed",
			"2026-07-18",
			"09:00:00",
			"2026-07-18",
			"10:00:00",
			"none",
			"회원 변경",
			"2026-07-15 09:00:00");
		final Long newerFirstId = insertAuditLog(
			reservationId,
			"admin-a",
			"admin",
			"schedule_changed",
			"confirmed",
			"confirmed",
			"2026-07-18",
			"10:00:00",
			"2026-07-18",
			"11:00:00",
			"none",
			"관리자 변경 A",
			"2026-07-16 09:00:00");
		final Long newerSecondId = insertAuditLog(
			reservationId,
			"admin-b",
			"admin",
			"reservation_cancelled",
			"confirmed",
			"cancelled",
			"2026-07-18",
			"11:00:00",
			"2026-07-18",
			"11:00:00",
			"none",
			"관리자 취소",
			"2026-07-16 09:00:00");

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.content[0].auditLogId").value(newerSecondId))
			.andExpect(jsonPath("$.content[1].auditLogId").value(newerFirstId))
			.andExpect(jsonPath("$.content[2].auditLogId").value(olderId));
	}

	@Test
	void 회원_예약_기간_행위자_변경유형을_함께_필터링한다() throws Exception {
		final Long memberId = insertMember("audit-filter-member", "김하늘", "010-3333-7788");
		final Long otherMemberId = insertMember("audit-filter-other", "박바다", "010-9999-0000");
		final Long reservationId = insertReservation(memberId);
		final Long otherReservationId = insertReservation(otherMemberId);
		final Long matchingId = insertAuditLog(
			reservationId,
			"audit-admin",
			"admin",
			"schedule_changed",
			"confirmed",
			"confirmed",
			"2026-07-20",
			"09:00:00",
			"2026-07-20",
			"10:00:00",
			"free_change_used",
			"무료 변경 승인",
			"2026-07-16 10:30:00");
		insertAuditLog(
			otherReservationId,
			"other-admin",
			"admin",
			"schedule_changed",
			"confirmed",
			"confirmed",
			"2026-07-20",
			"09:00:00",
			"2026-07-20",
			"10:00:00",
			"none",
			"다른 회원",
			"2026-07-16 10:30:00");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("keyword", "7788")
				.param("reservationId", reservationId.toString())
				.param("occurredDateFrom", "2026-07-16")
				.param("occurredDateTo", "2026-07-16")
				.param("actorType", "admin")
				.param("changeType", "schedule_changed"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].auditLogId").value(matchingId))
			.andExpect(jsonPath("$.content[0].reservationId").value(reservationId))
			.andExpect(jsonPath("$.content[0].memberId").value(memberId))
			.andExpect(jsonPath("$.content[0].memberName").value("김하늘"))
			.andExpect(jsonPath("$.content[0].actorAuthSubject").value("audit-admin"))
			.andExpect(jsonPath("$.content[0].actorType").value("admin"))
			.andExpect(jsonPath("$.content[0].changeType").value("schedule_changed"))
			.andExpect(jsonPath("$.content[0].fromStatus").value("confirmed"))
			.andExpect(jsonPath("$.content[0].toStatus").value("confirmed"))
			.andExpect(jsonPath("$.content[0].fromLessonDate").value("2026-07-20"))
			.andExpect(jsonPath("$.content[0].fromStartTime").value("09:00:00"))
			.andExpect(jsonPath("$.content[0].toStartTime").value("10:00:00"))
			.andExpect(jsonPath("$.content[0].couponAction").value("free_change_used"))
			.andExpect(jsonPath("$.content[0].memo").value("무료 변경 승인"))
			.andExpect(jsonPath("$.content[0].occurredAt").value("2026-07-16T10:30:00"));
	}

	@Test
	void 첫_중간_마지막과_빈_페이지의_전체_건수를_반환한다() throws Exception {
		final Long memberId = insertMember("audit-page-member", "페이지 회원", "010-4444-5555");
		final Long reservationId = insertReservation(memberId);
		final Long oldestId = insertScheduleChangeAudit(reservationId, "2026-07-15 09:00:00");
		final Long middleId = insertScheduleChangeAudit(reservationId, "2026-07-16 09:00:00");
		final Long newestId = insertScheduleChangeAudit(reservationId, "2026-07-17 09:00:00");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].auditLogId").value(newestId))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("page", "1")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.content[0].auditLogId").value(middleId));
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("page", "2")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].auditLogId").value(oldestId))
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("page", "3")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void 잘못된_필터와_날짜와_페이지를_통일된_오류로_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("actorType", "unknown"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_AUDIT_ACTOR_TYPE"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("changeType", "unknown"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_AUDIT_CHANGE_TYPE"));
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("occurredDateFrom", "2026-07-17")
				.param("occurredDateTo", "2026-07-16"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_AUDIT_DATE_RANGE"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("reservationId", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	@Test
	void 관리자만_조회하며_조회는_데이터를_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("audit-readonly-member", "불변 회원", "010-5555-6666");
		final Long reservationId = insertReservation(memberId);
		insertScheduleChangeAudit(reservationId, "2026-07-16 09:00:00");
		final List<String> before = databaseState();

		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT).with(adminJwt())).andExpect(status().isOk());

		assertThat(databaseState()).isEqualTo(before);
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

	private Long insertScheduleChangeAudit(Long reservationId, String createdAt) {
		return insertAuditLog(
			reservationId,
			"page-admin",
			"admin",
			"schedule_changed",
			"confirmed",
			"confirmed",
			"2026-07-20",
			"09:00:00",
			"2026-07-20",
			"10:00:00",
			"none",
			"페이지 변경",
			createdAt);
	}

	private Long insertAuditLog(
		Long reservationId,
		String actorAuthSubject,
		String actorType,
		String changeType,
		String fromStatus,
		String toStatus,
		String fromLessonDate,
		String fromStartTime,
		String toLessonDate,
		String toStartTime,
		String couponAction,
		String memo,
		String createdAt
	) {
		jdbcTemplate.update("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo, created_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			reservationId,
			actorAuthSubject,
			actorType,
			fromStatus,
			toStatus,
			fromLessonDate,
			fromStartTime,
			toLessonDate,
			toStartTime,
			changeType,
			couponAction,
			memo,
			createdAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private List<String> databaseState() {
		return jdbcTemplate.queryForList("""
			SELECT CONCAT(id, ':', reservation_id, ':', change_type, ':', memo)
			FROM reservation_change_logs ORDER BY id
			""", String.class);
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
