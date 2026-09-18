package com.horse.timeslots.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAvailableTimeSlotsApiTest {

	private static final String ENDPOINT = "/api/timeslots";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant CURRENT_INSTANT = Instant.parse("2026-07-21T01:00:00Z");
	private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 7, 21);
	private static final String CLASS_CAPACITIES = """
		{
		  "FIRST_RIDE": 2,
		  "ROUND_BEGINNER": 2,
		  "ROUND_TROT": 2,
		  "LARGE_ARENA_BEGINNER": 2,
		  "LARGE_ARENA_TROT": 2,
		  "CANTER_BEGINNER": 2,
		  "CANTER": 2,
		  "DRESSAGE": 2,
		  "JUMPING": 2
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 서울_기준_현재_시각을_고정한다() {
		when(clock.instant()).thenReturn(CURRENT_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 1,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
	}

	@Test
	void 인증되지_않은_시간대_조회는_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT)
				.param("date", futureDate().toString())
				.param("classType", "ROUND_BEGINNER"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 허용된_클래스의_잔여_정원을_조회하고_상태를_변경하지_않는다() throws Exception {
		final LocalDate date = futureDate();
		insertScheduleDate(date);
		insertMember("current-member", 1);
		final Long occupyingMemberId = insertMember("occupying-member", 1);
		insertTimeSlot(date, "09:00:00", 2, 2, false);
		insertPendingPaymentReservation(occupyingMemberId, date, "09:00:00");
		final Integer beforeCount = reservationCount();

		mockMvc.perform(get(ENDPOINT)
				.param("date", date.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("current-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value(date.toString()))
			.andExpect(jsonPath("$.classType").value("ROUND_BEGINNER"))
			.andExpect(jsonPath("$.timeSlots[0].startTime").value("09:00:00"))
			.andExpect(jsonPath("$.timeSlots[0].closed").value(false))
			.andExpect(jsonPath("$.timeSlots[0].reservable").value(true))
			.andExpect(jsonPath("$.timeSlots[0].remainingCapacity").value(1))
			.andExpect(jsonPath("$.timeSlots[0].unavailableReason").doesNotExist());

		assertThat(reservationCount()).isEqualTo(beforeCount);
	}

	@Test
	void 일정_설정이_SYNCING이면_예약_가능_시간대_조회를_차단한다() throws Exception {
		insertMember("syncing-member", 1);
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				pending_version = active_version + 1,
				sync_started_at = '2026-07-21 10:00:00',
				sync_started_by = 'm34-01-test-admin'
			WHERE id = 1
			""");

		mockMvc.perform(get(ENDPOINT)
				.param("date", futureDate().toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("syncing-member"))))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SCHEDULE_CONFIG_SYNC_IN_PROGRESS"));
	}

	@Test
	void 미래_retired_TEMPLATE만_숨기고_현재_시간과_다른_마감_원인은_유지한다() throws Exception {
		final LocalDate date = futureDate();
		insertScheduleDate(date);
		insertMember("retired-filter-member", 1);
		final Long changedTemplateId = insertTemplate("WEDNESDAY", "10:00:00", true);
		final Long inactiveTemplateId = insertTemplate("WEDNESDAY", "04:30:00", false);
		final Long holidayTemplateId = insertTemplate("WEDNESDAY", "12:00:00", true);
		insertTemplateTimeSlot(date, "04:30:00", inactiveTemplateId, false, false, true);
		insertTemplateTimeSlot(date, "09:00:00", changedTemplateId, false, false, true);
		insertTemplateTimeSlot(date, "10:00:00", changedTemplateId, false, false, false);
		insertTimeSlot(date, "11:00:00", 2, 2, true);
		insertTemplateTimeSlot(date, "12:00:00", holidayTemplateId, false, true, false);

		mockMvc.perform(get(ENDPOINT)
				.param("date", date.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("retired-filter-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots.length()").value(3))
			.andExpect(jsonPath("$.timeSlots[0].startTime").value("10:00:00"))
			.andExpect(jsonPath("$.timeSlots[0].closed").value(false))
			.andExpect(jsonPath("$.timeSlots[1].startTime").value("11:00:00"))
			.andExpect(jsonPath("$.timeSlots[1].unavailableReason").value("CLOSED"))
			.andExpect(jsonPath("$.timeSlots[2].startTime").value("12:00:00"))
			.andExpect(jsonPath("$.timeSlots[2].unavailableReason").value("CLOSED"));
	}

	@Test
	void 허용되지_않은_클래스와_마감과_만석_원인을_구분한다() throws Exception {
		final LocalDate date = futureDate();
		insertScheduleDate(date);
		insertMember("first-ride-member", 0);
		final Long occupyingMemberId = insertMember("full-member", 1);
		insertTimeSlot(date, "09:00:00", 2, 2, true);
		insertTimeSlot(date, "10:00:00", 1, 1, false);
		insertPendingPaymentReservation(occupyingMemberId, date, "10:00:00");

		mockMvc.perform(get(ENDPOINT)
				.param("date", date.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("first-ride-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots[0].reservable").value(false))
			.andExpect(jsonPath("$.timeSlots[0].unavailableReason").value("NOT_ELIGIBLE"))
			.andExpect(jsonPath("$.timeSlots[1].reservable").value(false))
			.andExpect(jsonPath("$.timeSlots[1].unavailableReason").value("NOT_ELIGIBLE"));

		insertMember("eligible-member", 1);

		mockMvc.perform(get(ENDPOINT)
				.param("date", date.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("eligible-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots[0].unavailableReason").value("CLOSED"))
			.andExpect(jsonPath("$.timeSlots[1].unavailableReason").value("FULL"));
	}

	@Test
	void 유효하지_않은_날짜와_클래스를_거부한다() throws Exception {
		insertMember("invalid-query-member", 1);

		mockMvc.perform(get(ENDPOINT)
				.param("date", "2020-01-01")
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("invalid-query-member"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TIMESLOT_INVALID_QUERY_DATE"));
		mockMvc.perform(get(ENDPOINT)
				.param("date", futureDate().toString())
				.param("classType", "INVALID")
				.with(jwt().jwt(token -> token.subject("invalid-query-member"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TIMESLOT_INVALID_CLASS_TYPE"));
	}

	@Test
	void 당일_수업은_정확히_3시간_이상_남은_시간대만_노출한다() throws Exception {
		insertScheduleDate(CURRENT_DATE);
		insertMember("same-day-member", 1);
		insertTimeSlot(CURRENT_DATE, "12:59:59", 2, 2, false);
		insertTimeSlot(CURRENT_DATE, "13:00:00", 2, 2, false);
		insertTimeSlot(CURRENT_DATE, "13:00:01", 2, 2, false);

		mockMvc.perform(get(ENDPOINT)
				.param("date", CURRENT_DATE.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("same-day-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots.length()").value(2))
			.andExpect(jsonPath("$.timeSlots[0].startTime").value("13:00:00"))
			.andExpect(jsonPath("$.timeSlots[0].reservable").value(true))
			.andExpect(jsonPath("$.timeSlots[1].startTime").value("13:00:01"))
			.andExpect(jsonPath("$.timeSlots[1].reservable").value(true));
	}

	@Test
	void CLOSING과_CLOSED_운영_날짜는_예약_가능_목록에서_숨긴다() throws Exception {
		final LocalDate date = futureDate();
		insertScheduleDate(date);
		insertMember("closed-date-member", 1);
		insertTimeSlot(date, "09:00:00", 2, 2, false);

		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'CLOSING', resume_status = 'NORMAL'
			WHERE schedule_date = ?
			""", date);
		assertDateHidden(date);
		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'CLOSED', resume_status = NULL
			WHERE schedule_date = ?
			""", date);
		assertDateHidden(date);
	}

	private void assertDateHidden(LocalDate date) throws Exception {
		mockMvc.perform(get(ENDPOINT)
				.param("date", date.toString())
				.param("classType", "ROUND_BEGINNER")
				.with(jwt().jwt(token -> token.subject("closed-date-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeSlots").isEmpty());
	}

	private Long insertMember(String authSubject, int generalRideCount) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, general_ride_count)
			VALUES (?, '시간대 회원', '010-0000-0000', ?)
			""", authSubject, generalRideCount);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private void insertScheduleDate(LocalDate scheduleDate) {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES (?, 'NORMAL', 1)
			""", scheduleDate);
	}

	private Long insertTemplate(String dayOfWeek, String startTime, boolean active) {
		jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, active, created_by, updated_by
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 2, 2, ?, ?, 'm34-02a-test', 'm34-02a-test')
			""", dayOfWeek, startTime, startTime, CLASS_CAPACITIES, active);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertTemplateTimeSlot(
		LocalDate lessonDate,
		String startTime,
		Long templateId,
		boolean adminClosed,
		boolean recurringHolidayClosed,
		boolean templateInactiveClosed
	) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json, admin_closed,
				recurring_holiday_closed, template_inactive_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'TEMPLATE', ?, 2, 2, ?, ?, ?, ?)
			""",
			lessonDate,
			startTime,
			startTime,
			templateId,
			CLASS_CAPACITIES,
			adminClosed,
			recurringHolidayClosed,
			templateInactiveClosed);
	}

	private void insertTimeSlot(
		LocalDate lessonDate,
		String startTime,
		int totalCapacity,
		int roundArenaCapacity,
		boolean closed
	) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date,
				start_time,
				total_capacity,
				round_arena_capacity,
				class_capacity_json,
				admin_closed
			) VALUES (?, ?, ?, ?, ?, ?)
			""", lessonDate, startTime, totalCapacity, roundArenaCapacity, CLASS_CAPACITIES, closed);
	}

	private void insertPendingPaymentReservation(Long memberId, LocalDate lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id,
				class_type,
				lesson_date,
				start_time,
				status,
				payment_source,
				payment_due_at,
				approval_requested_at
			) VALUES (
				?, 'ROUND_BEGINNER', ?, ?, 'pending_payment', 'single_payment',
				'2026-07-14 12:00:00', '2026-07-14 10:00:00'
			)
			""", memberId, lessonDate, startTime);
	}

	private Integer reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private LocalDate futureDate() {
		return CURRENT_DATE.plusDays(1);
	}
}
