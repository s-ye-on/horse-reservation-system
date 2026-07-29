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
class AdminPendingPaymentRestoreApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant RESTORE_INSTANT = Instant.parse("2026-07-15T01:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
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

	@BeforeEach
	void 데이터베이스와_복구_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDate();
		resetScheduleConfigGuard();
		when(clock.instant()).thenReturn(RESTORE_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_정원을_재확보하고_만료_예약과_감사_이력을_함께_복구한다() throws Exception {
		final Long memberId = insertMember("restore-member");
		insertTimeSlot("09:00:00", 1, 1);
		final Long reservationId = insertExpiredReservation(memberId, "09:00:00");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("입금 확인 후 복구")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.status").value("confirmed"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.adminConfirmedAt").value("2026-07-15T10:00:00+09:00"));

		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(changeLogCount(reservationId)).isEqualTo(1);
		assertThat(changeLogValue(reservationId, "change_type")).isEqualTo("payment_restored");
		assertThat(changeLogValue(reservationId, "actor_type")).isEqualTo("admin");
		assertThat(changeLogValue(reservationId, "actor_auth_subject")).isEqualTo("restore-admin");
		assertThat(changeLogValue(reservationId, "from_status")).isEqualTo("payment_expired");
		assertThat(changeLogValue(reservationId, "to_status")).isEqualTo("confirmed");
		assertThat(changeLogValue(reservationId, "coupon_action")).isEqualTo("none");
		assertThat(changeLogValue(reservationId, "memo")).isEqualTo("입금 확인 후 복구");
	}

	@Test
	void 정원이_가득_차면_예약과_감사_이력을_변경하지_않는다() throws Exception {
		final Long targetMemberId = insertMember("full-target-member");
		final Long occupyingMemberId = insertMember("full-occupying-member");
		insertTimeSlot("10:00:00", 1, 1);
		final Long reservationId = insertExpiredReservation(targetMemberId, "10:00:00");
		insertConfirmedReservation(occupyingMemberId, "10:00:00");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("정원 확인 복구")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_CAPACITY_EXCEEDED"));

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 같은_회원의_활성_예약이_있으면_만료_예약을_복구할_수_없다() throws Exception {
		final Long memberId = insertMember("duplicate-restore-member");
		insertTimeSlot("10:30:00", 2, 2);
		final Long expiredReservationId = insertExpiredReservation(memberId, "10:30:00");
		insertConfirmedReservation(memberId, "10:30:00");

		mockMvc.perform(post(endpoint(expiredReservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("중복 확인 후 복구")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_OVERLAPPING_ACTIVE_RESERVATION"));

		assertThat(reservationStatus(expiredReservationId)).isEqualTo("payment_expired");
		assertThat(changeLogCount(expiredReservationId)).isZero();
	}

	@Test
	void 마지막_한_자리를_경쟁하는_복구는_한_예약만_성공한다() throws Exception {
		final Long firstMemberId = insertMember("race-first-member");
		final Long secondMemberId = insertMember("race-second-member");
		insertTimeSlot("11:00:00", 1, 1);
		final Long firstReservationId = insertExpiredReservation(firstMemberId, "11:00:00");
		final Long secondReservationId = insertExpiredReservation(secondMemberId, "11:00:00");

		final List<Integer> statuses = concurrentRestores(List.of(firstReservationId, secondReservationId));

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(confirmedReservationCount("11:00:00")).isEqualTo(1);
		assertThat(changeLogCount(firstReservationId) + changeLogCount(secondReservationId)).isEqualTo(1);
	}

	@Test
	void 동일한_복구를_동시에_요청해도_상태와_이력을_한_번만_변경한다() throws Exception {
		final Long memberId = insertMember("idempotent-restore-member");
		insertTimeSlot("12:00:00", 1, 1);
		final Long reservationId = insertExpiredReservation(memberId, "12:00:00");

		final List<Integer> statuses = concurrentRestores(List.of(reservationId, reservationId));

		assertThat(statuses).containsExactly(200, 200);
		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
		assertThat(changeLogCount(reservationId)).isEqualTo(1);
	}

	@Test
	void 만료된_일회_결제_예약만_복구할_수_있다() throws Exception {
		final Long memberId = insertMember("invalid-state-member");
		insertTimeSlot("13:00:00", 1, 1);
		final Long reservationId = insertPendingPaymentReservation(memberId, "13:00:00");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("잘못된 상태 복구")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_payment");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 복구_메모는_필수이며_오백자를_초과할_수_없다() throws Exception {
		final Long memberId = insertMember("memo-validation-member");
		insertTimeSlot("14:00:00", 1, 1);
		final Long reservationId = insertExpiredReservation(memberId, "14:00:00");

		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(" ")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("가".repeat(501))))
			.andExpect(status().isBadRequest());

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 관리자만_만료_예약을_복구할_수_있다() throws Exception {
		final Long memberId = insertMember("restore-security-member");
		insertTimeSlot("15:00:00", 1, 1);
		final Long reservationId = insertExpiredReservation(memberId, "15:00:00");

		mockMvc.perform(post(endpoint(reservationId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("미인증 복구")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(endpoint(reservationId))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("회원 복구")))
			.andExpect(status().isForbidden());

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	private List<Integer> concurrentRestores(List<Long> reservationIds) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(reservationIds.size());
		final CountDownLatch ready = new CountDownLatch(reservationIds.size());
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = reservationIds.stream()
				.map(reservationId -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return restore(reservationId);
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

	private int restore(Long reservationId) throws Exception {
		return mockMvc.perform(post(endpoint(reservationId))
				.with(adminJwt("race-restore-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("동시 복구")))
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

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '복구 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertTimeSlot(String startTime, int totalCapacity, int roundArenaCapacity) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity, round_arena_capacity,
				class_capacity_json, admin_closed
			) VALUES (?, ?, ?, ?, ?, FALSE)
			""", LESSON_DATE, startTime, totalCapacity, roundArenaCapacity, CLASS_CAPACITIES);
	}

	private Long insertExpiredReservation(Long memberId, String startTime) {
		return insertReservation(memberId, startTime, "payment_expired");
	}

	private Long insertPendingPaymentReservation(Long memberId, String startTime) {
		return insertReservation(memberId, startTime, "pending_payment");
	}

	private Long insertReservation(Long memberId, String startTime, String status) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'single_payment',
				'2026-07-15 09:00:00', '2026-07-15 07:00:00')
			""", memberId, LESSON_DATE, startTime, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(Long memberId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'confirmed', 'single_payment',
				'2026-07-15 09:00:00', '2026-07-15 07:00:00', '2026-07-15 08:00:00')
			""", memberId, LESSON_DATE, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String endpoint(Long reservationId) {
		return "/api/admin/reservations/%d/restore-payment".formatted(reservationId);
	}

	private String request(String memo) {
		return """
			{
			  "memo": "%s"
			}
			""".formatted(memo);
	}

	private RequestPostProcessor adminJwt(String subject) {
		return jwt()
			.jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private Long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int confirmedReservationCount(String startTime) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservations
			WHERE lesson_date = ? AND start_time = ? AND status = 'confirmed'
			""", Integer.class, LESSON_DATE, startTime);
	}

	private int changeLogCount(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservation_change_logs WHERE reservation_id = ?",
			Integer.class,
			reservationId);
	}

	private String changeLogValue(Long reservationId, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT %s FROM reservation_change_logs WHERE reservation_id = ?".formatted(column),
			String.class,
			reservationId);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES (?, 'NORMAL', 1)
			""", LESSON_DATE);
	}

	private void resetScheduleConfigGuard() {
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
}
