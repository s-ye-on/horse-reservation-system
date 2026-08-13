package com.horse.schedules.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.schedules.application.ScheduleDateHorizonService;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminScheduleOperationsApiTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 10);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ScheduleDateHorizonService horizonService;

	@PersistenceContext
	EntityManager entityManager;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 운영_데이터_초기화() {
		given(clock.instant()).willReturn(Instant.parse("2026-08-01T01:00:00Z"));
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 1,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
		entityManager.clear();
		jdbcTemplate.update("""
			INSERT IGNORE INTO schedule_dates (
				schedule_date, status, applied_config_version
			) VALUES (?, 'NORMAL', 1)
			""", LESSON_DATE);
		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'NORMAL',
				resume_status = NULL,
				reason = NULL,
				changed_by = NULL,
				applied_config_version = 1,
				version = 0
			WHERE schedule_date = ?
			""", LESSON_DATE);
	}

	@Test
	void 동기화_상태를_조회하고_완료된_version의_수동_재시도를_차단한다() throws Exception {
		mockMvc.perform(get("/api/admin/schedule-sync").with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.activeVersion").value(1))
			.andExpect(jsonPath("$.remainingDateCount").isNumber())
			.andExpect(jsonPath("$.progressPercent").isNumber());

		mockMvc.perform(post("/api/admin/jobs/sync-schedule-occurrences/retry")
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pendingVersion\": 1}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_SYNC_RETRY_NOT_ALLOWED"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 현재_pending_version은_수동_재시도로_완료할_수_있다() throws Exception {
		horizonService.ensureHorizon();
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				pending_version = 2,
				sync_started_at = '2026-08-01 09:30:00',
				sync_started_by = 'admin-r11'
			WHERE id = 1
			""");
		entityManager.clear();

		mockMvc.perform(post("/api/admin/jobs/sync-schedule-occurrences/retry")
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pendingVersion\": 2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.targetVersion").value(2))
			.andExpect(jsonPath("$.synchronization.status").value("ACTIVE"))
			.andExpect(jsonPath("$.synchronization.activeVersion").value(2))
			.andExpect(jsonPath("$.synchronization.progressPercent").value(100));
	}

	@Test
	void 날짜_CLOSING은_영향_예약을_반환하고_전용_취소_후_CLOSED로_확정한다() throws Exception {
		final Long memberId = insertMember("date-member");
		final Long reservationId = insertReservation(memberId, "09:00:00", "09:45:00");

		final MvcResult closing = mockMvc.perform(post(
				"/api/admin/schedule-dates/{scheduleDate}/closing",
				LESSON_DATE)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("임시 휴무", 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSING"))
			.andExpect(jsonPath("$.initialReservationCount").value(1))
			.andExpect(jsonPath("$.resolvedReservationCount").value(0))
			.andExpect(jsonPath("$.progressPercent").value(0))
			.andExpect(jsonPath("$.activeReservationCount").value(1))
			.andExpect(jsonPath("$.reservations[0].reservationId").value(reservationId))
			.andReturn();
		final Number version = JsonPath.read(
			closing.getResponse().getContentAsString(),
			"$.version");

		mockMvc.perform(post("/api/admin/schedule-dates/{scheduleDate}/closing", LESSON_DATE)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("오래된 화면의 중복 요청", 0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_DATE_VERSION_CONFLICT"))
			.andExpect(jsonPath("$.details.currentVersion").value(version.longValue()));

		mockMvc.perform(post("/api/admin/schedule-dates/{scheduleDate}/close", LESSON_DATE)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("휴무 확정", version.longValue())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.details.activeReservationCount").value(1));

		mockMvc.perform(post(
				"/api/admin/schedule-dates/{scheduleDate}/reservations/{reservationId}/cancel-for-closure",
				LESSON_DATE,
				reservationId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"고객 연락 후 휴무 취소\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("none"));

		mockMvc.perform(post("/api/admin/schedule-dates/{scheduleDate}/close", LESSON_DATE)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("휴무 확정", version.longValue())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.resolvedReservationCount").value(1))
			.andExpect(jsonPath("$.progressPercent").value(100))
			.andExpect(jsonPath("$.activeReservationCount").value(0));
	}

	@Test
	void 개별_휴강은_고정_Impact를_유지하고_취소_완료_재개를_처리한다() throws Exception {
		final Long memberId = insertMember("closure-member");
		final Long timeSlotId = insertTimeSlot("10:00:00", "10:45:00");
		final Long reservationId = insertReservation(memberId, "10:00:00", "10:45:00");

		final MvcResult closureStarted = mockMvc.perform(
			post("/api/admin/timeslots/{timeSlotId}/closure", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequestWithoutVersion("우천 휴강")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.startedAt").value("2026-08-01T10:00:00+09:00"))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.unresolvedCount").value(1))
			.andExpect(jsonPath("$.impacts[0].reservationId").value(reservationId))
			.andReturn();
		final Number closureVersion = JsonPath.read(
			closureStarted.getResponse().getContentAsString(),
			"$.version");

		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				pending_version = 2,
				sync_started_at = '2026-08-01 09:30:00',
				sync_started_by = 'admin-r11'
			WHERE id = 1
			""");

		mockMvc.perform(post(
				"/api/admin/timeslots/{timeSlotId}/reservations/{reservationId}/cancel-for-closure",
				timeSlotId,
				reservationId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"고객 연락 후 휴강 취소\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("none"));

		mockMvc.perform(get("/api/admin/timeslots/{timeSlotId}/closure-impact", timeSlotId)
				.with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.resolvedCount").value(1))
			.andExpect(jsonPath("$.impacts[0].resolved").value(true));

		mockMvc.perform(post("/api/admin/timeslots/{timeSlotId}/closure/complete", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("오래된 화면의 완료 요청", closureVersion.longValue() + 1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_CLOSURE_VERSION_CONFLICT"))
			.andExpect(jsonPath("$.details.currentVersion").value(closureVersion.longValue()));

		mockMvc.perform(post("/api/admin/timeslots/{timeSlotId}/closure/complete", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("정리 완료", closureVersion.longValue())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.version").value(closureVersion.longValue() + 1));

		mockMvc.perform(post("/api/admin/timeslots/{timeSlotId}/closure/reopen", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequest("운영 재개", closureVersion.longValue() + 1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.adminClosed").value(false));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations WHERE id = ? AND status = 'cancelled'",
			Integer.class,
			reservationId)).isEqualTo(1);
	}

	@Test
	void 개별_휴강의_Coupon_예약은_RETURN으로_정리한다() throws Exception {
		final Long memberId = insertMember("coupon-closure-member");
		final Long timeSlotId = insertTimeSlot("11:00:00", "11:45:00");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);

		mockMvc.perform(post("/api/admin/timeslots/{timeSlotId}/closure", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(actionRequestWithoutVersion("우천 휴강")))
			.andExpect(status().isOk());

		mockMvc.perform(post(
				"/api/admin/timeslots/{timeSlotId}/reservations/{reservationId}/cancel-for-closure",
				timeSlotId,
				reservationId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"memo\":\"고객 연락 후 휴강 취소\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("return"));

		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?",
			Integer.class,
			couponId)).isZero();
	}

	private Long insertMember(String subject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '운영 회원', ?, FALSE)
			""", subject, "010-" + Math.abs(subject.hashCode() % 10000) + "-0000");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(String startTime, String endTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ?, 'MANUAL', 8, 4,
				JSON_OBJECT(
					'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 3, 'LARGE_ARENA_TROT', 3,
					'DRESSAGE', 1, 'JUMPING', 1
				))
			""", LESSON_DATE, startTime, endTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId, String startTime, String endTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'pending_payment',
				'single_payment', '2026-08-01 12:00:00', '2026-08-01 09:00:00')
			""", memberId, LESSON_DATE, startTime, endTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'admin-r11')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '11:00:00', '11:45:00',
				'pending_admin_approval', 'coupon', ?, '2026-08-01 09:00:00')
			""", memberId, LESSON_DATE, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, 'held', 0, '2026-08-01 09:00:00', 'member')
			""", couponId, reservationId, memberId, memberId);
	}

	private String actionRequest(String reason, long expectedVersion) {
		return """
			{
			  "reason": "%s",
			  "expectedVersion": %d
			}
			""".formatted(reason, expectedVersion);
	}

	private String actionRequestWithoutVersion(String reason) {
		return """
			{
			  "reason": "%s"
			}
			""".formatted(reason);
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("admin-r11"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}
}
