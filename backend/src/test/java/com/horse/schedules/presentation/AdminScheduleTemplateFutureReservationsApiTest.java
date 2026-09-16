package com.horse.schedules.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
class AdminScheduleTemplateFutureReservationsApiTest {

	private static final Instant NOW = Instant.parse("2026-09-16T01:00:00Z");
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);
	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,"LARGE_ARENA_BEGINNER":3,
		"LARGE_ARENA_TROT":3,"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 테스트_데이터와_서울_현재시각을_초기화한다() {
		clearFixture();
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearFixture();
	}

	@Test
	void 해당_Template의_아직_시작하지_않은_점유_예약만_건수와_목록으로_반환한다() throws Exception {
		final long targetTemplateId = insertTemplate("WEDNESDAY", "11:00:00");
		final long otherTemplateId = insertTemplate("WEDNESDAY", "12:00:00");
		insertTimeSlot(targetTemplateId, TODAY.minusDays(1), "11:00:00");
		insertTimeSlot(targetTemplateId, TODAY, "10:00:00");
		insertTimeSlot(targetTemplateId, TODAY, "11:00:00");
		insertTimeSlot(otherTemplateId, TODAY, "12:00:00");

		final long approvalId = insertReservation(
			insertMember("approval", "승인 대기 회원"),
			TODAY,
			"11:00:00",
			"ROUND_BEGINNER",
			"pending_admin_approval");
		final long paymentId = insertReservation(
			insertMember("payment", "입금 대기 회원"),
			TODAY,
			"11:00:00",
			"DRESSAGE",
			"pending_payment");
		final long confirmedId = insertReservation(
			insertMember("confirmed", "확정 회원"),
			TODAY,
			"11:00:00",
			"JUMPING",
			"confirmed");
		insertExcludedStatuses(TODAY, "11:00:00");
		insertReservation(
			insertMember("past", "과거 연결 회원"),
			TODAY.minusDays(1),
			"11:00:00",
			"ROUND_TROT",
			"confirmed");
		insertReservation(
			insertMember("started", "시작 시각 회원"),
			TODAY,
			"10:00:00",
			"ROUND_TROT",
			"confirmed");
		insertReservation(
			insertMember("other-template", "다른 템플릿 회원"),
			TODAY,
			"12:00:00",
			"ROUND_TROT",
			"confirmed");

		mockMvc.perform(get(endpoint(targetTemplateId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateId").value(targetTemplateId))
			.andExpect(jsonPath("$.reservationCount").value(3))
			.andExpect(jsonPath("$.reservations.length()").value(3))
			.andExpect(jsonPath("$.reservations[0].reservationId").value(approvalId))
			.andExpect(jsonPath("$.reservations[0].lessonDate").value("2026-09-16"))
			.andExpect(jsonPath("$.reservations[0].startTime").value("11:00:00"))
			.andExpect(jsonPath("$.reservations[0].endTime").value("11:45:00"))
			.andExpect(jsonPath("$.reservations[0].memberId").isNumber())
			.andExpect(jsonPath("$.reservations[0].memberName").value("승인 대기 회원"))
			.andExpect(jsonPath("$.reservations[0].memberPhone").value("010-3402-0000"))
			.andExpect(jsonPath("$.reservations[0].ridingClass").value("ROUND_BEGINNER"))
			.andExpect(jsonPath("$.reservations[0].status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.reservations[1].reservationId").value(paymentId))
			.andExpect(jsonPath("$.reservations[1].ridingClass").value("DRESSAGE"))
			.andExpect(jsonPath("$.reservations[1].status").value("pending_payment"))
			.andExpect(jsonPath("$.reservations[2].reservationId").value(confirmedId))
			.andExpect(jsonPath("$.reservations[2].ridingClass").value("JUMPING"))
			.andExpect(jsonPath("$.reservations[2].status").value("confirmed"));
	}

	@Test
	void 점유_예약이_없으면_0과_빈_목록을_반환하고_없는_Template은_거부한다() throws Exception {
		final long templateId = insertTemplate("THURSDAY", "09:00:00");
		insertTimeSlot(templateId, TODAY.plusDays(1), "09:00:00");
		insertReservation(
			insertMember("cancelled-only", "취소 회원"),
			TODAY.plusDays(1),
			"09:00:00",
			"ROUND_TROT",
			"cancelled");

		mockMvc.perform(get(endpoint(templateId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationCount").value(0))
			.andExpect(jsonPath("$.reservations").isEmpty());
		mockMvc.perform(get(endpoint(Long.MAX_VALUE)).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("SCHEDULE_TEMPLATE_NOT_FOUND"));
	}

	@Test
	void 관리자만_미래_점유_예약을_조회할_수_있다() throws Exception {
		final long templateId = insertTemplate("FRIDAY", "09:00:00");

		mockMvc.perform(get(endpoint(templateId))).andExpect(status().isUnauthorized());
		mockMvc.perform(get(endpoint(templateId)).with(memberJwt())).andExpect(status().isForbidden());
	}

	private void insertExcludedStatuses(LocalDate lessonDate, String startTime) {
		for (String status : new String[]{
			"payment_expired", "approval_expired", "completed", "rejected", "cancelled", "no_show"
		}) {
			insertReservation(
				insertMember("excluded-" + status, "제외 " + status),
				lessonDate,
				startTime,
				"ROUND_TROT",
				status);
		}
	}

	private long insertTemplate(String dayOfWeek, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, active, created_by, updated_by
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 8, 4, ?, TRUE, 'm34-02-test', 'm34-02-test')
			""", dayOfWeek, startTime, startTime, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertTimeSlot(long templateId, LocalDate lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'TEMPLATE', ?, 8, 4, ?)
			""", lessonDate, startTime, startTime, templateId, CLASS_CAPACITIES);
	}

	private long insertMember(String suffix, String name) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-3402-0000', FALSE)
			""", "m34-02-api-" + suffix, name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertReservation(
		long memberId,
		LocalDate lessonDate,
		String startTime,
		String ridingClass,
		String status
	) {
		final Long couponId = switch (status) {
			case "pending_admin_approval", "approval_expired" -> insertCoupon(memberId);
			default -> null;
		};
		final String paymentSource = couponId == null ? "single_payment" : "coupon";
		final String paymentDueAt = couponId == null ? "2026-09-16 12:00:00" : null;
		final String confirmedAt = switch (status) {
			case "confirmed", "completed", "no_show" -> "2026-09-16 09:00:00";
			default -> null;
		};
		final String rejectedAt = "rejected".equals(status) ? "2026-09-16 09:00:00" : null;
		final String rejectedBy = "rejected".equals(status) ? "m34-02-admin" : null;
		final String rejectionReason = "rejected".equals(status) ? "미래 점유 조회 제외" : null;
		final String cancelledAt = "cancelled".equals(status) ? "2026-09-16 09:00:00" : null;
		final String cancellationResponsibility = "cancelled".equals(status) ? "member" : null;

		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at,
				rejected_at, rejected_by, rejection_reason, cancelled_at, cancellation_responsibility
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '2026-09-15 09:00:00', ?, ?, ?, ?, ?, ?)
			""",
			memberId,
			ridingClass,
			lessonDate,
			startTime,
			status,
			paymentSource,
			couponId,
			paymentDueAt,
			confirmedAt,
			rejectedAt,
			rejectedBy,
			rejectionReason,
			cancelledAt,
			cancellationResponsibility);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertCoupon(long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 0, 'active', 'm34-02-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String endpoint(long templateId) {
		return "/api/admin/schedule-templates/" + templateId + "/future-occupying-reservations";
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private void clearFixture() {
		jdbcTemplate.update("""
			DELETE FROM reservations
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'm34-02-api-%')
			""");
		jdbcTemplate.update("""
			DELETE FROM coupons
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'm34-02-api-%')
			""");
		jdbcTemplate.update("""
			DELETE FROM member_class_progression_audit_logs
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'm34-02-api-%')
			""");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'm34-02-api-%'");
		jdbcTemplate.update("DELETE FROM time_slot_capacities WHERE template_id IN (SELECT id FROM regular_schedule_templates WHERE created_by = 'm34-02-test')");
		jdbcTemplate.update("DELETE FROM regular_schedule_templates WHERE created_by = 'm34-02-test'");
	}
}
