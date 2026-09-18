package com.horse.reservations.presentation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

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
class AdminWeeklyOperationsCalendarApiTest {

	private static final String ENDPOINT = "/api/admin/reservations/weekly-operations-calendar";
	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,"LARGE_ARENA_BEGINNER":3,
		"LARGE_ARENA_TROT":3,"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant QUERY_INSTANT = Instant.parse("2026-12-30T15:30:00Z");
	private static final LocalDate FIXTURE_FROM = LocalDate.of(2026, 12, 20);
	private static final LocalDate FIXTURE_TO = LocalDate.of(2027, 1, 10);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 테스트_데이터와_서울_현재시각을_초기화한다() {
		clearFixture();
		when(clock.instant()).thenReturn(QUERY_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearFixture();
	}

	@Test
	void 기본_현재주는_서울_기준_월요일부터_일요일이며_빈_materialized_TimeSlot도_반환한다() throws Exception {
		insertTimeSlot(LocalDate.of(2026, 12, 27), "08:00:00", false);
		final Long mondaySlotId = insertTimeSlot(LocalDate.of(2026, 12, 28), "09:00:00", false);
		final Long sundaySlotId = insertTimeSlot(LocalDate.of(2027, 1, 3), "10:00:00", true);
		insertTimeSlot(LocalDate.of(2027, 1, 4), "11:00:00", false);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.referenceDate").value("2026-12-31"))
			.andExpect(jsonPath("$.weekStartDate").value("2026-12-28"))
			.andExpect(jsonPath("$.weekEndDate").value("2027-01-03"))
			.andExpect(jsonPath("$.timeSlots.length()").value(2))
			.andExpect(jsonPath("$.timeSlots[0].timeSlotId").value(mondaySlotId))
			.andExpect(jsonPath("$.timeSlots[0].lessonDate").value("2026-12-28"))
			.andExpect(jsonPath("$.timeSlots[0].startTime").value("09:00:00"))
			.andExpect(jsonPath("$.timeSlots[0].endTime").value("09:45:00"))
			.andExpect(jsonPath("$.timeSlots[0].totalCapacity").value(8))
			.andExpect(jsonPath("$.timeSlots[0].roundArenaCapacity").value(4))
			.andExpect(jsonPath("$.timeSlots[0].closed").value(false))
			.andExpect(jsonPath("$.timeSlots[0].reservations").isEmpty())
			.andExpect(jsonPath("$.timeSlots[1].timeSlotId").value(sundaySlotId))
			.andExpect(jsonPath("$.timeSlots[1].lessonDate").value("2027-01-03"))
			.andExpect(jsonPath("$.timeSlots[1].closed").value(true))
			.andExpect(jsonPath("$.timeSlots[1].reservations").isEmpty());
	}

	@Test
	void 미래_retired_TEMPLATE은_숨기고_과거와_다른_마감_원인과_정리_대상은_보존한다() throws Exception {
		final Long changedTemplateId = insertTemplate("FRIDAY", "10:00:00", true);
		final Long inactiveTemplateId = insertTemplate("SATURDAY", "04:30:00", false);
		final Long holidayTemplateId = insertTemplate("FRIDAY", "12:00:00", true);
		final Long pastRetiredId = insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2026, 12, 30), "09:00:00", false, false, true);
		final Long startedBeforeCurrentTimeId = insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2026, 12, 31), "00:29:59", false, false, true);
		final Long startingAtCurrentTimeId = insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2026, 12, 31), "00:30:00", false, false, true);
		insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2026, 12, 31), "00:30:01", false, false, true);
		insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2027, 1, 1), "09:00:00", false, false, true);
		final Long currentTemplateId = insertTemplateTimeSlot(
			changedTemplateId, LocalDate.of(2027, 1, 1), "10:00:00", false, false, false);
		final Long adminClosedId = insertTimeSlot(LocalDate.of(2027, 1, 1), "11:00:00", true);
		final Long holidayClosedId = insertTemplateTimeSlot(
			holidayTemplateId, LocalDate.of(2027, 1, 1), "12:00:00", false, true, false);
		insertTemplateTimeSlot(
			inactiveTemplateId, LocalDate.of(2027, 1, 2), "04:30:00", false, false, true);
		final Long occupyingMemberId = insertMember("retired-occupying", "운영 종료 예약 회원");
		insertReservation(
			occupyingMemberId,
			"ROUND_BEGINNER",
			LocalDate.of(2027, 1, 2),
			"04:30:00",
			"confirmed");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("referenceDate", "2026-12-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots.length()").value(6))
			.andExpect(jsonPath("$.timeSlots[0].timeSlotId").value(pastRetiredId))
			.andExpect(jsonPath("$.timeSlots[1].timeSlotId").value(startedBeforeCurrentTimeId))
			.andExpect(jsonPath("$.timeSlots[2].timeSlotId").value(startingAtCurrentTimeId))
			.andExpect(jsonPath("$.timeSlots[3].timeSlotId").value(currentTemplateId))
			.andExpect(jsonPath("$.timeSlots[3].closed").value(false))
			.andExpect(jsonPath("$.timeSlots[4].timeSlotId").value(adminClosedId))
			.andExpect(jsonPath("$.timeSlots[4].closed").value(true))
			.andExpect(jsonPath("$.timeSlots[5].timeSlotId").value(holidayClosedId))
			.andExpect(jsonPath("$.timeSlots[5].closed").value(true));

		mockMvc.perform(get("/api/admin/schedule-templates/{templateId}/future-occupying-reservations",
				inactiveTemplateId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationCount").value(1))
			.andExpect(jsonPath("$.reservations[0].memberId").value(occupyingMemberId));
	}

	@Test
	void 점유_상태와_COMPLETED만_회원_클래스_원래상태로_표시하고_제외이력만_있는_슬롯은_비워둔다() throws Exception {
		insertTimeSlot(LocalDate.of(2026, 12, 30), "10:00:00", false);
		insertTimeSlot(LocalDate.of(2026, 12, 30), "11:00:00", false);
		final Long approvalMemberId = insertMember("approval", "승인 대기 회원");
		final Long paymentMemberId = insertMember("payment", "입금 대기 회원");
		final Long confirmedMemberId = insertMember("confirmed", "확정 회원");
		final Long completedMemberId = insertMember("completed", "완료 회원");
		insertReservation(approvalMemberId, "ROUND_BEGINNER", "10:00:00", "pending_admin_approval");
		insertReservation(paymentMemberId, "DRESSAGE", "10:00:00", "pending_payment");
		insertReservation(confirmedMemberId, "JUMPING", "10:00:00", "confirmed");
		insertReservation(completedMemberId, "CANTER", "10:00:00", "completed");
		insertExcludedStatuses("10:00:00");
		insertReservation(insertMember("excluded-only", "취소 이력 회원"), "ROUND_TROT", "11:00:00", "cancelled");
		insertReservation(insertMember("no-slot", "슬롯 없는 예약 회원"), "ROUND_TROT", "12:00:00", "confirmed");

		mockMvc.perform(get(ENDPOINT)
			.with(adminJwt())
			.param("referenceDate", "2026-12-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots.length()").value(2))
			.andExpect(jsonPath("$.timeSlots[0].reservations.length()").value(4))
			.andExpect(jsonPath("$.timeSlots[0].reservations[0].memberId").value(approvalMemberId))
			.andExpect(jsonPath("$.timeSlots[0].reservations[0].memberName").value("승인 대기 회원"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[0].ridingClass").value("ROUND_BEGINNER"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[0].status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[1].memberId").value(paymentMemberId))
			.andExpect(jsonPath("$.timeSlots[0].reservations[1].ridingClass").value("DRESSAGE"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[1].status").value("pending_payment"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[2].memberId").value(confirmedMemberId))
			.andExpect(jsonPath("$.timeSlots[0].reservations[2].ridingClass").value("JUMPING"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[2].status").value("confirmed"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[3].memberId").value(completedMemberId))
			.andExpect(jsonPath("$.timeSlots[0].reservations[3].ridingClass").value("CANTER"))
			.andExpect(jsonPath("$.timeSlots[0].reservations[3].status").value("completed"))
			.andExpect(jsonPath("$.timeSlots[1].reservations").isEmpty());
	}

	@Test
	void 관리자만_조회할_수_있고_잘못된_기준일은_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("referenceDate", "2026-13-40"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	private void insertExcludedStatuses(String startTime) {
		for (String status : new String[]{
			"payment_expired", "approval_expired", "rejected", "cancelled", "no_show"
		}) {
			insertReservation(
				insertMember("excluded-" + status, "제외 " + status),
				"ROUND_TROT",
				startTime,
				status);
		}
	}

	private Long insertTimeSlot(LocalDate lessonDate, String startTime, boolean adminClosed) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json, admin_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'MANUAL', 8, 4, ?, ?)
			""", lessonDate, startTime, startTime, CLASS_CAPACITIES, adminClosed);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTemplate(String dayOfWeek, String startTime, boolean active) {
		jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, active, created_by, updated_by
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 8, 4, ?, ?, 'm34-02a-test', 'm34-02a-test')
			""", dayOfWeek, startTime, startTime, CLASS_CAPACITIES, active);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTemplateTimeSlot(
		Long templateId,
		LocalDate lessonDate,
		String startTime,
		boolean adminClosed,
		boolean recurringHolidayClosed,
		boolean templateInactiveClosed
	) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json, admin_closed,
				recurring_holiday_closed, template_inactive_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'TEMPLATE', ?, 8, 4, ?, ?, ?, ?)
			""",
			lessonDate,
			startTime,
			startTime,
			templateId,
			CLASS_CAPACITIES,
			adminClosed,
			recurringHolidayClosed,
			templateInactiveClosed);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertMember(String suffix, String name) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-7000-0000', FALSE)
			""", "weekly-api-" + suffix, name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertReservation(Long memberId, String ridingClass, String startTime, String status) {
		insertReservation(memberId, ridingClass, LocalDate.of(2026, 12, 30), startTime, status);
	}

	private void insertReservation(
		Long memberId,
		String ridingClass,
		LocalDate lessonDate,
		String startTime,
		String status
	) {
		final Long couponId = switch (status) {
			case "pending_admin_approval", "approval_expired" -> insertCoupon(memberId);
			default -> null;
		};
		final String paymentSource = couponId == null ? "single_payment" : "coupon";
		final String paymentDueAt = couponId == null ? "2026-12-30 12:00:00" : null;
		final String confirmedAt = switch (status) {
			case "confirmed", "completed", "no_show" -> "2026-12-30 10:00:00";
			default -> null;
		};
		final String rejectedAt = "rejected".equals(status) ? "2026-12-30 10:00:00" : null;
		final String rejectedBy = "rejected".equals(status) ? "weekly-admin" : null;
		final String rejectionReason = "rejected".equals(status) ? "주간 캘린더 제외 검증" : null;
		final String cancelledAt = "cancelled".equals(status) ? "2026-12-30 10:00:00" : null;
		final String cancellationResponsibility = "cancelled".equals(status) ? "member" : null;

		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at,
				rejected_at, rejected_by, rejection_reason, cancelled_at, cancellation_responsibility
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '2026-12-29 09:00:00', ?, ?, ?, ?, ?, ?)
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
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 0, 'active', 'weekly-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'weekly-api-%')
			""");
		jdbcTemplate.update("""
			DELETE FROM coupons
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'weekly-api-%')
			""");
		jdbcTemplate.update("""
			DELETE FROM member_class_progression_audit_logs
			WHERE member_id IN (SELECT id FROM members WHERE auth_subject LIKE 'weekly-api-%')
			""");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'weekly-api-%'");
		jdbcTemplate.update(
			"DELETE FROM time_slot_capacities WHERE lesson_date BETWEEN ? AND ?",
			FIXTURE_FROM,
			FIXTURE_TO);
		jdbcTemplate.update("DELETE FROM regular_schedule_templates WHERE created_by = 'm34-02a-test'");
	}

}
