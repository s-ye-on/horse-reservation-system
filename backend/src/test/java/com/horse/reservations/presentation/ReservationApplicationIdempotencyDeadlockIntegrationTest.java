package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReservationApplicationIdempotencyDeadlockIntegrationTest {

	private static final String ENDPOINT = "/api/reservations";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 6);
	private static final int HELPER_LOCK_COUNT = 50;
	private static final String CLASS_CAPACITIES = """
		{
		  "FIRST_RIDE": 8,
		  "ROUND_BEGINNER": 4,
		  "ROUND_TROT": 4,
		  "LARGE_ARENA_BEGINNER": 8,
		  "LARGE_ARENA_TROT": 8,
		  "CANTER_BEGINNER": 8,
		  "CANTER": 8,
		  "DRESSAGE": 8,
		  "JUMPING": 8
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoBean
	Clock clock;

	@MockitoSpyBean
	JacksonReservationApplicationResponseEncoder responseEncoder;

	@BeforeEach
	void 데이터베이스와_기준_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(Instant.parse("2026-07-29T00:00:00Z"));
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 완료_직전_MySQL_deadlock을_전체_재시도해_논리_예약을_한_번만_반영한다()
		throws Exception {
		final Fixture fixture = insertFixture("deadlock-member", LESSON_DATE, "09:00:00");
		final Long firstProbeId = insertMember("deadlock-probe-first");
		final Long secondProbeId = insertMember("deadlock-probe-second");
		final List<Long> helperLockIds = insertHelperMembers();
		final CountDownLatch helperFirstLockAcquired = new CountDownLatch(1);
		final CountDownLatch requestFirstLockAcquired = new CountDownLatch(1);
		final AtomicInteger serializationCount = new AtomicInteger();
		doAnswer(invocation -> {
			final int invocationNumber = serializationCount.incrementAndGet();
			if (invocationNumber == 1) {
				jdbcTemplate.update(
					"UPDATE members SET name = name WHERE id = ?",
					firstProbeId);
				requestFirstLockAcquired.countDown();
				if (!helperFirstLockAcquired.await(10, TimeUnit.SECONDS)) {
					throw new AssertionError("deadlock helper lock barrier timed out");
				}
				jdbcTemplate.update(
					"UPDATE members SET name = name WHERE id = ?",
					secondProbeId);
			}
			return invocation.callRealMethod();
		}).when(responseEncoder).encode(any());

		final ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			final Future<?> helper = executor.submit(() -> runDeadlockHelper(
				firstProbeId,
				secondProbeId,
				helperLockIds,
				helperFirstLockAcquired,
				requestFirstLockAcquired));
			final MvcResult result = apply(fixture);
			helper.get();

			assertThat(result.getResponse().getStatus()).isEqualTo(201);
			assertThat(serializationCount.get()).isEqualTo(2);
			assertThat(reservationCount()).isOne();
			assertThat(ledgerCount()).isOne();
			assertThat(totalHeldCount()).isOne();
			assertThat(heldAuditCount()).isOne();

			final MvcResult replay = apply(fixture);
			assertThat(replay.getResponse().getStatus()).isEqualTo(201);
			assertThat(replay.getResponse().getContentAsString())
				.isEqualTo(result.getResponse().getContentAsString());
			assertThat(reservationCount()).isOne();
			assertThat(ledgerCount()).isOne();
			assertThat(totalHeldCount()).isOne();
			assertThat(heldAuditCount()).isOne();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void runDeadlockHelper(
		Long firstProbeId,
		Long secondProbeId,
		List<Long> helperLockIds,
		CountDownLatch helperFirstLockAcquired,
		CountDownLatch requestFirstLockAcquired
	) {
		final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(status -> {
			jdbcTemplate.update(
				"UPDATE members SET name = name WHERE id = ?",
				secondProbeId);
			for (Long helperLockId : helperLockIds) {
				jdbcTemplate.update(
					"UPDATE members SET name = CONCAT(name, 'x') WHERE id = ?",
					helperLockId);
			}
			helperFirstLockAcquired.countDown();
			try {
				if (!requestFirstLockAcquired.await(10, TimeUnit.SECONDS)) {
					throw new AssertionError("deadlock request lock barrier timed out");
				}
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
			jdbcTemplate.update(
				"UPDATE members SET name = name WHERE id = ?",
				firstProbeId);
		});
	}

	private List<Long> insertHelperMembers() {
		final List<Long> memberIds = new ArrayList<>();
		for (int index = 0; index < HELPER_LOCK_COUNT; index++) {
			memberIds.add(insertMember("deadlock-helper-" + index));
		}
		return List.copyOf(memberIds);
	}

	private Fixture insertFixture(
		String authSubject,
		LocalDate lessonDate,
		String startTime
	) {
		final Long memberId = insertMember(authSubject);
		insertCoupon(memberId);
		final Long timeSlotId = insertTimeSlot(lessonDate, startTime);
		return new Fixture(authSubject, "key-" + authSubject, timeSlotId);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, 'deadlock 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 0, 'deadlock-test-admin')
			""", memberId);
	}

	private Long insertTimeSlot(LocalDate lessonDate, String startTime) {
		insertScheduleDate(lessonDate);
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, admin_closed
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 8, 4, ?, FALSE)
			""", lessonDate, startTime, startTime, CLASS_CAPACITIES);
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

	private String request(Long timeSlotId) {
		return """
			{
			  "timeSlotId": %d,
			  "classType": "FIRST_RIDE"
			}
			""".formatted(timeSlotId);
	}

	private MvcResult apply(Fixture fixture) throws Exception {
		return mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(fixture.authSubject()))
				.header("Idempotency-Key", fixture.idempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(fixture.timeSlotId())))
			.andReturn();
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt()
			.jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private int reservationCount() {
		return count("SELECT COUNT(*) FROM reservations");
	}

	private int ledgerCount() {
		return count("SELECT COUNT(*) FROM reservation_application_idempotencies");
	}

	private int totalHeldCount() {
		return count("SELECT COALESCE(SUM(held_count), 0) FROM coupons");
	}

	private int heldAuditCount() {
		return count("SELECT COUNT(*) FROM coupon_usage_logs WHERE action = 'held'");
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

	private record Fixture(
		String authSubject,
		String idempotencyKey,
		Long timeSlotId
	) {
	}
}
