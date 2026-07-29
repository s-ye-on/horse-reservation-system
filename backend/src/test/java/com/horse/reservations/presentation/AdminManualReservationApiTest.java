package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.sql.Types;
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
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminManualReservationApiTest {

	private static final String ADMIN_ENDPOINT = "/api/admin/reservations";
	private static final String MEMBER_ENDPOINT = "/api/reservations";
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant REQUEST_INSTANT = Instant.parse("2026-07-29T00:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 5);
	private static final String ADMIN_SUBJECT = "manual-reservation-admin";
	private static final String CLASS_CAPACITIES = """
		{
		  "FIRST_RIDE": 8,
		  "ROUND_BEGINNER": 4,
		  "ROUND_TROT": 4,
		  "LARGE_ARENA_BEGINNER": 8,
		  "LARGE_ARENA_TROT": 8,
		  "DRESSAGE": 8,
		  "JUMPING": 8
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@MockitoSpyBean
	JacksonReservationApplicationResponseEncoder responseEncoder;

	@BeforeEach
	void 데이터베이스와_기준_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(REQUEST_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_가장_먼저_만료되는_본인_쿠폰을_점유하고_예약을_즉시_확정한다()
		throws Exception {
		final Long memberId = insertMember("admin-coupon-target");
		insertCoupon(memberId, null, "2026-07-01 09:00:00");
		insertCoupon(memberId, "2026-10-01 00:00:00", "2026-07-02 09:00:00");
		final Long selectedCouponId = insertCoupon(
			memberId,
			"2026-09-01 00:00:00",
			"2026-07-03 09:00:00");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "10:00:00", 8, 4);

		final MvcResult first = createByAdmin(
			"admin-coupon-key",
			request(memberId, timeSlotId, "FIRST_RIDE", "전화 접수"));
		final MvcResult replay = createByAdmin(
			"admin-coupon-key",
			request(memberId, timeSlotId, "FIRST_RIDE", "전화 접수"));

		assertThat(first.getResponse().getStatus()).isEqualTo(201);
		assertThat(replay.getResponse().getContentAsString())
			.isEqualTo(first.getResponse().getContentAsString());
		assertThat(first.getResponse().getContentAsString())
			.contains("\"status\":\"confirmed\"")
			.contains("\"couponId\":" + selectedCouponId);
		assertThat(reservationCount()).isOne();
		assertThat(couponHeldCount(selectedCouponId)).isOne();
		assertThat(couponRemainingCount(selectedCouponId)).isEqualTo(10);
		assertThat(totalHeldCount()).isOne();
		assertThat(couponHoldAuditCount("admin")).isOne();
		assertThat(couponUsageActionCount("confirmed")).isOne();
		assertThat(adminCreationAuditCount()).isOne();
		assertThat(adminCreationAuditMemo()).isEqualTo("전화 접수");
		assertThat(completedLedgerCount("admin_reservation_create")).isOne();
		mockMvc.perform(get("/api/admin/audit-logs")
				.with(adminJwt())
				.param("changeType", "admin_reservation_created"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].couponId").value(selectedCouponId))
			.andExpect(jsonPath("$.content[0].paymentDueAt").doesNotExist());
	}

	@Test
	void 관리자_수동_쿠폰_예약은_확정_원장을_남겨_수업을_완료할_수_있다()
		throws Exception {
		final Long memberId = insertMember("admin-coupon-completion-target");
		final Long couponId = insertCoupon(
			memberId,
			"2026-09-01 00:00:00",
			"2026-07-03 09:00:00");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "10:00:00", 8, 4);

		createByAdmin(
			"admin-coupon-completion-key",
			request(memberId, timeSlotId, "FIRST_RIDE", "완료 흐름 검증"));
		final Long reservationId = jdbcTemplate.queryForObject(
			"SELECT id FROM reservations",
			Long.class);
		when(clock.instant()).thenReturn(Instant.parse("2026-08-05T01:46:00Z"));

		mockMvc.perform(post("/api/admin/reservations/{reservationId}/complete", reservationId)
				.with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("completed"));

		assertThat(reservationCount("completed")).isOne();
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(couponRemainingCount(couponId)).isEqualTo(9);
		assertThat(couponUsageActionCount("used")).isOne();
	}

	@Test
	void 쿠폰이_없으면_입금대기로_생성하고_수업_시작보다_빠른_두시간_마감을_반환한다()
		throws Exception {
		final Long memberId = insertMember("admin-payment-target");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "11:00:00", 8, 4);

		mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(adminJwt())
				.header(IDEMPOTENCY_HEADER, "admin-payment-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(memberId, timeSlotId, "FIRST_RIDE", "현장 결제 예정")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_payment"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.paymentDueAt").value("2026-07-29T11:00:00+09:00"))
			.andExpect(jsonPath("$.coupon").doesNotExist());

		assertThat(reservationCount("pending_payment")).isOne();
		assertThat(couponUsageLogCount()).isZero();
		assertThat(adminCreationAuditCount()).isOne();
		mockMvc.perform(get("/api/admin/audit-logs")
				.with(adminJwt())
				.param("changeType", "admin_reservation_created"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].couponId").doesNotExist())
			.andExpect(jsonPath("$.content[0].paymentDueAt")
				.value("2026-07-29T11:00:00+09:00"));
	}

	@Test
	void 회원과_비인증_요청은_관리자_수동_예약을_생성할_수_없다() throws Exception {
		final Long memberId = insertMember("admin-auth-target");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "12:00:00", 8, 4);
		final String body = request(memberId, timeSlotId, "FIRST_RIDE", "권한 검증");

		mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(memberJwt("member-role-subject"))
				.header(IDEMPOTENCY_HEADER, "member-forbidden-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(ADMIN_ENDPOINT)
				.header(IDEMPOTENCY_HEADER, "anonymous-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());

		assertThat(reservationCount()).isZero();
		assertThat(ledgerCount()).isZero();
	}

	@Test
	void 같은_key에_다른_회원이나_사유를_사용하면_409를_반환한다() throws Exception {
		final Long firstMemberId = insertMember("admin-key-first-target");
		final Long secondMemberId = insertMember("admin-key-second-target");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "13:30:00", 8, 4);

		createByAdmin(
			"admin-payload-key",
			request(firstMemberId, timeSlotId, "FIRST_RIDE", "첫 요청"));
		mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(adminJwt())
				.header(IDEMPOTENCY_HEADER, "admin-payload-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(secondMemberId, timeSlotId, "FIRST_RIDE", "다른 요청")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_IDEMPOTENCY_KEY_CONFLICT"));

		assertThat(reservationCount()).isOne();
		assertThat(adminCreationAuditCount()).isOne();
		assertThat(ledgerCount()).isOne();
	}

	@Test
	void 관리자는_회원_마감_이후에도_시작_전까지_예약하지만_정각부터는_거부된다()
		throws Exception {
		final Long memberId = insertMember("admin-time-boundary-target");
		final Long timeSlotId = insertTimeSlot(
			LocalDate.of(2026, 7, 29),
			"10:00:00",
			8,
			4);

		createByAdmin(
			"before-start-key",
			request(memberId, timeSlotId, "FIRST_RIDE", "당일 전화 접수"));
		assertThat(reservationCount()).isOne();

		clearReservationsOnly();
		when(clock.instant()).thenReturn(Instant.parse("2026-07-29T01:00:00Z"));
		mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(adminJwt())
				.header(IDEMPOTENCY_HEADER, "at-start-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(memberId, timeSlotId, "FIRST_RIDE", "정각 요청")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));

		assertThat(reservationCount()).isZero();
	}

	@Test
	void 설정_동기화와_휴무_마감_Template_비활성은_관리자_예약을_차단한다()
		throws Exception {
		final Long memberId = insertMember("admin-schedule-gate-target");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "14:30:00", 8, 4);

		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				pending_version = active_version + 1,
				sync_started_at = NOW(6),
				sync_started_by = 'test-admin'
			WHERE id = 1
			""");
		assertBlocked(
			"syncing-key",
			memberId,
			timeSlotId,
			"SCHEDULE_CONFIG_SYNC_IN_PROGRESS");

		resetScheduleConfigGuard();
		jdbcTemplate.update(
			"UPDATE schedule_dates SET status = 'OPEN' WHERE schedule_date = ?",
			LESSON_DATE);
		createByAdmin(
			"open-date-key",
			request(memberId, timeSlotId, "FIRST_RIDE", "OPEN 날짜 검증"));
		assertThat(reservationCount()).isOne();
		clearReservationsOnly();

		for (String statusValue : List.of("CLOSING", "CLOSED")) {
			if ("CLOSING".equals(statusValue)) {
				jdbcTemplate.update("""
					UPDATE schedule_dates
					SET status = 'CLOSING',
						resume_status = 'NORMAL',
						reason = '테스트 휴무',
						changed_by = 'test-admin'
					WHERE schedule_date = ?
					""", LESSON_DATE);
			}
			else {
				jdbcTemplate.update("""
					UPDATE schedule_dates
					SET status = 'CLOSED',
						resume_status = NULL,
						reason = '테스트 휴무',
						changed_by = 'test-admin'
					WHERE schedule_date = ?
					""", LESSON_DATE);
			}
			assertBlocked(
				statusValue.toLowerCase() + "-date-key",
				memberId,
				timeSlotId,
				null);
		}

		jdbcTemplate.update(
			"""
				UPDATE schedule_dates
				SET status = 'NORMAL',
					resume_status = NULL,
					reason = NULL,
					changed_by = NULL
				WHERE schedule_date = ?
				""",
			LESSON_DATE);
		for (String column : List.of(
			"admin_closed",
			"recurring_holiday_closed",
			"template_inactive_closed")) {
			jdbcTemplate.update(
				"UPDATE time_slot_capacities SET " + column + " = TRUE WHERE id = ?",
				timeSlotId);
			assertBlocked(column + "-key", memberId, timeSlotId, "TIMESLOT_CLOSED");
			jdbcTemplate.update(
				"UPDATE time_slot_capacities SET " + column + " = FALSE WHERE id = ?",
				timeSlotId);
		}

		assertThat(reservationCount()).isZero();
		assertThat(ledgerCount()).isZero();
	}

	@Test
	void 정원_부족과_동일_회원_수업_구간_overlap은_관리자_예약을_차단한다()
		throws Exception {
		final Long memberId = insertMember("admin-capacity-overlap-target");
		final Long fullTimeSlotId = insertTimeSlot(LESSON_DATE, "15:30:00", 1, 1);
		insertOccupyingReservation(
			insertMember("capacity-occupant"),
			LESSON_DATE,
			"15:30:00");

		assertBlocked(
			"capacity-key",
			memberId,
			fullTimeSlotId,
			"TIMESLOT_CAPACITY_EXCEEDED");

		final Long overlapTimeSlotId = insertTimeSlot(LESSON_DATE, "16:30:00", 8, 4);
		insertOccupyingReservation(memberId, LESSON_DATE, "16:00:00");
		assertBlocked(
			"overlap-key",
			memberId,
			overlapTimeSlotId,
			"RESERVATION_OVERLAPPING_ACTIVE_RESERVATION");

		assertThat(reservationCount()).isEqualTo(2);
		assertThat(adminCreationAuditCount()).isZero();
	}

	@Test
	void 응답_생성_장애는_예약_쿠폰_감사_원장을_모두_rollback한다() throws Exception {
		final Long memberId = insertMember("admin-rollback-target");
		final Long couponId = insertCoupon(
			memberId,
			"2026-10-01 00:00:00",
			"2026-07-01 09:00:00");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "09:00:00", 8, 4);
		doThrow(new ReservationException(ExceptionCode.RESERVATION_RESPONSE_SERIALIZATION_FAILED))
			.when(responseEncoder)
			.encode(any());

		mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(adminJwt())
				.header(IDEMPOTENCY_HEADER, "admin-rollback-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(memberId, timeSlotId, "FIRST_RIDE", "rollback 검증")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("RESERVATION_RESPONSE_SERIALIZATION_FAILED"));

		assertThat(reservationCount()).isZero();
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(couponUsageLogCount()).isZero();
		assertThat(adminCreationAuditCount()).isZero();
		assertThat(ledgerCount()).isZero();
	}

	@Test
	void 회원과_관리자가_같은_회원의_같은_TimeSlot을_동시에_예약하면_하나만_성공한다()
		throws Exception {
		final String memberSubject = "member-admin-race-target";
		final Long memberId = insertMember(memberSubject);
		final Long couponId = insertCoupon(
			memberId,
			"2026-10-01 00:00:00",
			"2026-07-01 09:00:00");
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "10:30:00", 1, 1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final Future<MvcResult> memberRequest = executor.submit(() -> {
				ready.countDown();
				start.await();
				return createByMember(memberSubject, timeSlotId);
			});
			final Future<MvcResult> adminRequest = executor.submit(() -> {
				ready.countDown();
				start.await();
				return createByAdmin(
					"admin-member-race-key",
					request(memberId, timeSlotId, "FIRST_RIDE", "경쟁 요청"));
			});
			ready.await();
			start.countDown();

			final List<Integer> statuses = List.of(
				getResult(memberRequest).getResponse().getStatus(),
				getResult(adminRequest).getResponse().getStatus());
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(reservationCount()).isOne();
		assertThat(couponHeldCount(couponId)).isOne();
		assertThat(couponHoldAuditCount(null)).isOne();
		assertThat(ledgerCount()).isOne();
		assertThat(adminCreationAuditCount()).isLessThanOrEqualTo(1);
	}

	private void assertBlocked(
		String key,
		Long memberId,
		Long timeSlotId,
		String expectedCode
	) throws Exception {
		final var action = mockMvc.perform(post(ADMIN_ENDPOINT)
			.with(adminJwt())
			.header(IDEMPOTENCY_HEADER, key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(request(memberId, timeSlotId, "FIRST_RIDE", "차단 검증")));
		if ("SCHEDULE_CONFIG_SYNC_IN_PROGRESS".equals(expectedCode)) {
			action.andExpect(status().isServiceUnavailable());
		}
		else {
			action.andExpect(status().is4xxClientError());
		}
		if (expectedCode != null) {
			action.andExpect(jsonPath("$.code").value(expectedCode));
		}
	}

	private MvcResult createByAdmin(String key, String body) throws Exception {
		return mockMvc.perform(post(ADMIN_ENDPOINT)
				.with(adminJwt())
				.header(IDEMPOTENCY_HEADER, key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andReturn();
	}

	private MvcResult createByMember(String authSubject, Long timeSlotId) throws Exception {
		return mockMvc.perform(post(MEMBER_ENDPOINT)
				.with(memberJwt(authSubject))
				.header(IDEMPOTENCY_HEADER, "member-admin-race-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "timeSlotId": %d,
					  "classType": "FIRST_RIDE"
					}
					""".formatted(timeSlotId)))
			.andReturn();
	}

	private MvcResult getResult(Future<MvcResult> future) {
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
			VALUES (?, '수동 예약 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(
		Long memberId,
		String expiresAt,
		String createdAt
	) {
		final Timestamp expiry = expiresAt == null ? null : Timestamp.valueOf(expiresAt);
		final Object firstRideAt = expiry == null
			? new SqlParameterValue(Types.TIMESTAMP, null)
			: Timestamp.valueOf(expiry.toLocalDateTime().minusMonths(3));
		final Object expiryParameter = expiry == null
			? new SqlParameterValue(Types.TIMESTAMP, null)
			: expiry;
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, created_by, created_at
			) VALUES (?, 'general', 10, 10, 0, ?, ?, 'manual-test-admin', ?)
			""",
			memberId,
			firstRideAt,
			expiryParameter,
			Timestamp.valueOf(createdAt));
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(
		LocalDate lessonDate,
		String startTime,
		int totalCapacity,
		int roundArenaCapacity
	) {
		insertScheduleDate(lessonDate);
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, admin_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), ?, ?, ?, FALSE)
			""",
			lessonDate,
			startTime,
			startTime,
			totalCapacity,
			roundArenaCapacity,
			CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertScheduleDate(LocalDate lessonDate) {
		jdbcTemplate.update("""
			INSERT IGNORE INTO schedule_dates (schedule_date, status, applied_config_version)
			SELECT ?, 'NORMAL', active_version
			FROM schedule_config_guard
			WHERE id = 1
			""", lessonDate);
	}

	private void insertOccupyingReservation(
		Long memberId,
		LocalDate lessonDate,
		String startTime
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (
				?, 'FIRST_RIDE', ?, ?, ADDTIME(?, '00:45:00'), 'confirmed',
				'single_payment', TIMESTAMP(?, ?), TIMESTAMP(?, ?), TIMESTAMP(?, ?)
			)
			""",
			memberId,
			lessonDate,
			startTime,
			startTime,
			lessonDate,
			startTime,
			lessonDate,
			startTime,
			lessonDate,
			startTime);
	}

	private String request(
		Long memberId,
		Long timeSlotId,
		String classType,
		String reason
	) {
		return """
			{
			  "memberId": %d,
			  "timeSlotId": %d,
			  "classType": "%s",
			  "reason": "%s"
			}
			""".formatted(memberId, timeSlotId, classType, reason);
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject(ADMIN_SUBJECT))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt()
			.jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private int reservationCount() {
		return count("SELECT COUNT(*) FROM reservations");
	}

	private int reservationCount(String status) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations WHERE status = ?",
			Integer.class,
			status);
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?",
			Integer.class,
			couponId);
	}

	private int couponRemainingCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT remaining_count FROM coupons WHERE id = ?",
			Integer.class,
			couponId);
	}

	private int totalHeldCount() {
		return count("SELECT COALESCE(SUM(held_count), 0) FROM coupons");
	}

	private int couponHoldAuditCount(String actorType) {
		if (actorType == null) {
			return count("SELECT COUNT(*) FROM coupon_usage_logs WHERE action = 'held'");
		}
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE action = 'held' AND actor_type = ?
			""", Integer.class, actorType);
	}

	private int couponUsageLogCount() {
		return count("SELECT COUNT(*) FROM coupon_usage_logs");
	}

	private int couponUsageActionCount(String action) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE action = ?",
			Integer.class,
			action);
	}

	private int adminCreationAuditCount() {
		return count("""
			SELECT COUNT(*)
			FROM reservation_change_logs
			WHERE change_type = 'admin_reservation_created'
			""");
	}

	private String adminCreationAuditMemo() {
		return jdbcTemplate.queryForObject("""
			SELECT memo
			FROM reservation_change_logs
			WHERE change_type = 'admin_reservation_created'
			""", String.class);
	}

	private int ledgerCount() {
		return count("SELECT COUNT(*) FROM reservation_application_idempotencies");
	}

	private int completedLedgerCount(String operation) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservation_application_idempotencies
			WHERE operation = ? AND status = 'completed'
			""", Integer.class, operation);
	}

	private int count(String sql) {
		return jdbcTemplate.queryForObject(sql, Integer.class);
	}

	private void clearReservationsOnly() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservation_application_idempotencies");
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
	}

	private void clearDatabase() {
		resetScheduleConfigGuard();
		clearReservationsOnly();
		jdbcTemplate.update("DELETE FROM time_slot_closure_impacts");
		jdbcTemplate.update("DELETE FROM time_slot_closures");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void resetScheduleConfigGuard() {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL,
				last_failed_at = NULL,
				last_failure_code = NULL,
				last_failure_summary = NULL
			WHERE id = 1
			""");
	}
}
