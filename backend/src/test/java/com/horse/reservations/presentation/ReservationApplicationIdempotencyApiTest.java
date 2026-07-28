package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
class ReservationApplicationIdempotencyApiTest {

	private static final String ENDPOINT = "/api/reservations";
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant REQUEST_INSTANT = Instant.parse("2026-07-29T00:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 5);
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
	void Idempotency_Key가_없으면_명확한_오류를_반환한다() throws Exception {
		final String authSubject = "missing-key-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot("09:00:00");

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_IDEMPOTENCY_KEY_REQUIRED"));

		assertThat(reservationCount()).isZero();
		assertThat(ledgerCount()).isZero();
	}

	@Test
	void 같은_key와_정규화된_같은_요청은_최초_status와_body를_그대로_replay한다()
		throws Exception {
		final String authSubject = "same-request-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot("10:00:00");
		final String key = "same-request-key";

		final MvcResult first = apply(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));
		final MvcResult replay = apply(
			authSubject,
			key,
			reorderedRequest(timeSlotId, "FIRST_RIDE"));

		assertThat(first.getResponse().getStatus()).isEqualTo(201);
		assertThat(replay.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
		assertThat(replay.getResponse().getContentAsString())
			.isEqualTo(first.getResponse().getContentAsString());
		assertThat(reservationCount()).isOne();
		assertThat(ledgerCount()).isOne();
		assertThat(completedLedgerCount()).isOne();
	}

	@Test
	void 같은_key를_다른_예약_요청에_사용하면_409를_반환한다() throws Exception {
		final String authSubject = "key-conflict-member";
		insertMember(authSubject);
		final Long firstTimeSlotId = insertTimeSlot("11:00:00");
		final Long secondTimeSlotId = insertTimeSlot("12:00:00");
		final String key = "different-request-key";

		apply(authSubject, key, request(firstTimeSlotId, "FIRST_RIDE"));
		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.header(IDEMPOTENCY_HEADER, key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(secondTimeSlotId, "FIRST_RIDE")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_IDEMPOTENCY_KEY_CONFLICT"));

		assertThat(reservationCount()).isOne();
		assertThat(ledgerCount()).isOne();
	}

	@Test
	void 다른_회원은_같은_key를_각자_사용할_수_있다() throws Exception {
		final String firstSubject = "same-key-first-member";
		final String secondSubject = "same-key-second-member";
		insertMember(firstSubject);
		insertMember(secondSubject);
		final Long timeSlotId = insertTimeSlot("13:00:00");
		final String key = "shared-across-members";

		final MvcResult first = apply(firstSubject, key, request(timeSlotId, "FIRST_RIDE"));
		final MvcResult second = apply(secondSubject, key, request(timeSlotId, "FIRST_RIDE"));

		assertThat(first.getResponse().getStatus()).isEqualTo(201);
		assertThat(second.getResponse().getStatus()).isEqualTo(201);
		assertThat(reservationCount()).isEqualTo(2);
		assertThat(ledgerCount()).isEqualTo(2);
	}

	@Test
	void 쿠폰_예약의_동일_key_동시_요청은_예약과_점유와_감사를_한_번만_반영한다()
		throws Exception {
		final String authSubject = "concurrent-coupon-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long timeSlotId = insertTimeSlot("14:00:00");
		final String key = "concurrent-coupon-key";

		final List<MvcResult> results = concurrentSameRequest(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));

		assertThat(results).allSatisfy(result ->
			assertThat(result.getResponse().getStatus()).isEqualTo(201));
		assertThat(results.get(0).getResponse().getContentAsString())
			.isEqualTo(results.get(1).getResponse().getContentAsString());
		assertThat(reservationCount()).isOne();
		assertThat(couponHeldCount(couponId)).isOne();
		assertThat(heldAuditCount(couponId)).isOne();
		assertThat(ledgerCount()).isOne();
	}

	@Test
	void 무쿠폰_입금대기_동일_key_동시_요청도_예약을_한_번만_생성한다() throws Exception {
		final String authSubject = "concurrent-payment-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot("15:00:00");
		final String key = "concurrent-payment-key";

		final List<MvcResult> results = concurrentSameRequest(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));

		assertThat(results).allSatisfy(result ->
			assertThat(result.getResponse().getStatus()).isEqualTo(201));
		assertThat(results.get(0).getResponse().getContentAsString())
			.isEqualTo(results.get(1).getResponse().getContentAsString());
		assertThat(reservationCount()).isOne();
		assertThat(pendingPaymentCount()).isOne();
		assertThat(ledgerCount()).isOne();
		assertThat(couponUsageLogCount()).isZero();
	}

	@Test
	void 완료_기록_직전_장애는_전체를_rollback하고_같은_key로_재시도할_수_있다()
		throws Exception {
		final String authSubject = "rollback-retry-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long timeSlotId = insertTimeSlot("16:00:00");
		final String key = "rollback-retry-key";
		doThrow(new ReservationException(ExceptionCode.RESERVATION_RESPONSE_SERIALIZATION_FAILED))
			.doCallRealMethod()
			.when(responseEncoder)
			.encode(any());

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.header(IDEMPOTENCY_HEADER, key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("RESERVATION_RESPONSE_SERIALIZATION_FAILED"));

		assertThat(reservationCount()).isZero();
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(heldAuditCount(couponId)).isZero();
		assertThat(ledgerCount()).isZero();

		final MvcResult retried = apply(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));

		assertThat(retried.getResponse().getStatus()).isEqualTo(201);
		assertThat(reservationCount()).isOne();
		assertThat(couponHeldCount(couponId)).isOne();
		assertThat(heldAuditCount(couponId)).isOne();
		assertThat(completedLedgerCount()).isOne();
	}

	@Test
	void 응답_전달_실패를_가정한_재호출은_저장된_성공_응답만_반환한다() throws Exception {
		final String authSubject = "delivery-failure-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long timeSlotId = insertTimeSlot("16:30:00");
		final String key = "delivery-failure-key";

		final MvcResult deliveredButIgnored = apply(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));
		final MvcResult replay = apply(
			authSubject,
			key,
			request(timeSlotId, "FIRST_RIDE"));

		assertThat(replay.getResponse().getStatus())
			.isEqualTo(deliveredButIgnored.getResponse().getStatus());
		assertThat(replay.getResponse().getContentAsString())
			.isEqualTo(deliveredButIgnored.getResponse().getContentAsString());
		assertThat(reservationCount()).isOne();
		assertThat(couponHeldCount(couponId)).isOne();
		assertThat(heldAuditCount(couponId)).isOne();
		assertThat(ledgerCount()).isOne();
	}

	private List<MvcResult> concurrentSameRequest(
		String authSubject,
		String key,
		String body
	) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<MvcResult>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return apply(authSubject, key, body);
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

	private MvcResult apply(String authSubject, String key, String body) throws Exception {
		return mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.header(IDEMPOTENCY_HEADER, key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
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
			VALUES (?, '멱등성 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 0, 'idempotency-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(String startTime) {
		insertScheduleDate();
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, admin_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 8, 4, ?, FALSE)
			""", LESSON_DATE, startTime, startTime, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT IGNORE INTO schedule_dates (schedule_date, status, applied_config_version)
			SELECT ?, 'NORMAL', active_version
			FROM schedule_config_guard
			WHERE id = 1
			""", LESSON_DATE);
	}

	private String request(Long timeSlotId, String classType) {
		return """
			{
			  "timeSlotId": %d,
			  "classType": "%s"
			}
			""".formatted(timeSlotId, classType);
	}

	private String reorderedRequest(Long timeSlotId, String classType) {
		return """
			{ "classType" : "%s", "timeSlotId" : %d }
			""".formatted(classType, timeSlotId);
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt()
			.jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private int reservationCount() {
		return count("SELECT COUNT(*) FROM reservations");
	}

	private int pendingPaymentCount() {
		return count("SELECT COUNT(*) FROM reservations WHERE status = 'pending_payment'");
	}

	private int ledgerCount() {
		return count("SELECT COUNT(*) FROM reservation_application_idempotencies");
	}

	private int completedLedgerCount() {
		return count("""
			SELECT COUNT(*)
			FROM reservation_application_idempotencies
			WHERE status = 'completed'
			  AND http_status = 201
			  AND response_body IS NOT NULL
			  AND reservation_id IS NOT NULL
			""");
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?",
			Integer.class,
			couponId);
	}

	private int heldAuditCount(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE coupon_id = ? AND action = 'held'
			""", Integer.class, couponId);
	}

	private int couponUsageLogCount() {
		return count("SELECT COUNT(*) FROM coupon_usage_logs");
	}

	private int count(String sql) {
		return jdbcTemplate.queryForObject(sql, Integer.class);
	}

	private void clearDatabase() {
		resetScheduleConfigGuard();
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservation_application_idempotencies");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
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
