package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.horse.TestcontainersConfiguration;
import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.RecurringHolidayRule;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RecurringHolidayRuleServiceIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
	private static final String CLASS_CAPACITIES_JSON = """
		{
			"FIRST_RIDE": 2,
			"ROUND_BEGINNER": 2,
			"ROUND_TROT": 2,
			"LARGE_ARENA_BEGINNER": 3,
			"LARGE_ARENA_TROT": 3,
			"CANTER_BEGINNER": 3,
			"CANTER": 3,
			"DRESSAGE": 1,
			"JUMPING": 1
		}
		""";

	@Autowired
	RecurringHolidayRuleService holidayService;

	@Autowired
	RecurringHolidayRuleRepository holidayRepository;

	@Autowired
	RegularScheduleTemplateRepository templateRepository;

	@Autowired
	ScheduleAuditLogRepository auditLogRepository;

	@Autowired
	ScheduleDateHorizonService horizonService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 정기_휴일_fixture를_초기화한다() {
		given(clock.getZone()).willReturn(SEOUL_ZONE);
		given(clock.instant()).willReturn(NOW);
		jdbcTemplate.update("DELETE FROM schedule_audit_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM regular_schedule_templates");
		jdbcTemplate.update("DELETE FROM recurring_holiday_rules");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
		resetGuardActive(1L);
		horizonService.ensureHorizon();
	}

	@Test
	void 월요일_정기_휴일을_생성하고_영향과_SYNCING과_감사를_반영한다() {
		createTemplateTimeSlotWithActiveReservation();
		createManualTimeSlot();

		final RecurringHolidayMutationResult result = holidayService.create(command(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 7, 1),
			null,
			1L,
			"월요일 정기 휴일 등록"));

		assertThat(result.rule().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(result.rule().effectiveTo()).isNull();
		assertThat(result.pendingConfigVersion()).isEqualTo(2L);
		assertThat(result.impact().previous())
			.isEqualTo(RecurringHolidayImpactCounts.zero());
		assertThat(result.impact().current().affectedDateCount()).isPositive();
		assertThat(result.impact().current().templateTimeSlotCount()).isOne();
		assertThat(result.impact().current().activeReservationCount()).isOne();
		assertThat(result.impact().combined()).isEqualTo(result.impact().current());
		assertGuard("SYNCING", 1L, 2L);
		assertAudit(result.rule().id(), "CREATED", 2L);
		assertTemplateAndManualTimeSlotUnchanged();
	}

	@Test
	void 종료일_NULL은_무기한으로_해석해_이후_겹침을_거부한다() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 7, 1),
			null,
			"무기한 월요일 휴일",
			"schedule-admin"));

		assertScheduleException(
			() -> holidayService.create(command(
				DayOfWeek.MONDAY,
				LocalDate.of(2030, 1, 1),
				LocalDate.of(2030, 12, 31),
				1L,
				"겹침 등록")),
			ExceptionCode.SCHEDULE_RECURRING_HOLIDAY_OVERLAP);

		assertThat(holidayRepository.count()).isOne();
		assertGuard("ACTIVE", 1L, null);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM schedule_audit_logs",
			Long.class)).isZero();
	}

	@Test
	void 종료일과_다음_시작일이_다르면_연속_기간을_등록할_수_있다() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 7, 1),
			LocalDate.of(2026, 7, 31),
			"7월 월요일 휴일",
			"schedule-admin"));

		final RecurringHolidayMutationResult result = holidayService.create(command(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 8, 1),
			LocalDate.of(2026, 8, 31),
			1L,
			"8월 월요일 휴일"));

		assertThat(result.rule().effectiveFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(holidayRepository.count()).isEqualTo(2);
	}

	@Test
	void 기존_종료일과_신규_시작일이_같으면_겹침으로_거부한다() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 7, 1),
			LocalDate.of(2026, 7, 31),
			"7월 월요일 휴일",
			"schedule-admin"));

		assertScheduleException(
			() -> holidayService.create(command(
				DayOfWeek.MONDAY,
				LocalDate.of(2026, 7, 31),
				LocalDate.of(2026, 8, 31),
				1L,
				"포함 경계 겹침")),
			ExceptionCode.SCHEDULE_RECURRING_HOLIDAY_OVERLAP);

		assertThat(holidayRepository.count()).isOne();
		assertGuard("ACTIVE", 1L, null);
	}

	@Test
	void 정기_휴일을_변경하고_비활성화한_뒤_다시_활성화한다() {
		final RecurringHolidayRule rule = holidayRepository.saveAndFlush(
			RecurringHolidayRule.create(
				DayOfWeek.TUESDAY,
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 31),
				"화요일 휴일",
				"schedule-admin"));
		createManualTimeSlot();

		final RecurringHolidayMutationResult updated = holidayService.update(
			rule.getId(),
			command(
				DayOfWeek.WEDNESDAY,
				LocalDate.of(2026, 8, 1),
				LocalDate.of(2026, 9, 30),
				1L,
				"수요일로 변경"));
		assertThat(updated.rule().dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
		assertThat(updated.impact().previous().affectedDateCount()).isPositive();
		assertThat(updated.impact().current().affectedDateCount()).isPositive();
		assertThat(updated.impact().combined().affectedDateCount())
			.isGreaterThan(updated.impact().previous().affectedDateCount());

		resetGuardActive(2L);
		final RecurringHolidayMutationResult deactivated = holidayService.deactivate(
			rule.getId(),
			2L,
			"schedule-admin",
			"정기 휴일 중단");
		assertThat(deactivated.rule().active()).isFalse();

		resetGuardActive(3L);
		final RecurringHolidayMutationResult activated = holidayService.activate(
			rule.getId(),
			3L,
			"schedule-admin",
			"정기 휴일 재개");
		assertThat(activated.rule().active()).isTrue();
		assertThat(auditLogRepository.findAllByTarget(
			ScheduleAuditTargetType.RECURRING_HOLIDAY,
			"recurring-holiday:" + rule.getId()))
			.extracting(ScheduleAuditLog::getAction)
			.containsExactly("UPDATED", "DEACTIVATED", "ACTIVATED");
		assertManualTimeSlotUnchanged();
	}

	@Test
	void 겹치는_비활성_정기_휴일은_다른_활성_규칙이_있으면_재활성화할_수_없다() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.FRIDAY,
			LocalDate.of(2026, 7, 1),
			null,
			"활성 금요일 휴일",
			"schedule-admin"));
		final RecurringHolidayRule inactive = RecurringHolidayRule.create(
			DayOfWeek.FRIDAY,
			LocalDate.of(2026, 8, 1),
			LocalDate.of(2026, 8, 31),
			"비활성 금요일 휴일",
			"schedule-admin");
		inactive.deactivate("schedule-admin");
		holidayRepository.saveAndFlush(inactive);

		assertScheduleException(
			() -> holidayService.activate(
				inactive.getId(),
				1L,
				"schedule-admin",
				"겹침 재활성화"),
			ExceptionCode.SCHEDULE_RECURRING_HOLIDAY_OVERLAP);

		assertThat(holidayRepository.findById(inactive.getId()).orElseThrow().isActive())
			.isFalse();
		assertGuard("ACTIVE", 1L, null);
	}

	@Test
	void stale_설정_version과_SYNCING_중_변경을_거부한다() {
		assertScheduleException(
			() -> holidayService.create(command(
				DayOfWeek.THURSDAY,
				LocalDate.of(2026, 7, 1),
				null,
				2L,
				"stale 등록")),
			ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);

		holidayService.create(command(
			DayOfWeek.THURSDAY,
			LocalDate.of(2026, 7, 1),
			null,
			1L,
			"첫 등록"));

		assertScheduleException(
			() -> holidayService.create(command(
				DayOfWeek.FRIDAY,
				LocalDate.of(2026, 7, 1),
				null,
				1L,
				"동기화 중 등록")),
			ExceptionCode.SCHEDULE_CONFIG_SYNC_IN_PROGRESS);
	}

	@Test
	void 동시_겹침_등록은_하나만_성공한다() throws Exception {
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<RecurringHolidayMutationResult> first = executor.submit(
				() -> createAfterSignal(
					ready,
					start,
					command(
						DayOfWeek.SATURDAY,
						LocalDate.of(2026, 7, 1),
						LocalDate.of(2026, 8, 31),
						1L,
						"동시 등록 1")));
			final Future<RecurringHolidayMutationResult> second = executor.submit(
				() -> createAfterSignal(
					ready,
					start,
					command(
						DayOfWeek.SATURDAY,
						LocalDate.of(2026, 8, 1),
						null,
						1L,
						"동시 등록 2")));
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final List<Future<RecurringHolidayMutationResult>> requests = List.of(first, second);
			assertThat(requests.stream().filter(this::completedSuccessfully).count()).isOne();
			assertThat(requests.stream().filter(this::failedForSyncing).count()).isOne();
			assertThat(holidayRepository.count()).isOne();
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM schedule_audit_logs",
				Long.class)).isOne();
		}
	}

	private RecurringHolidayMutationResult createAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		RecurringHolidayRuleCommand command
	) throws InterruptedException {
		ready.countDown();
		assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
		return holidayService.create(command);
	}

	private boolean completedSuccessfully(Future<RecurringHolidayMutationResult> request) {
		try {
			request.get(10, TimeUnit.SECONDS);
			return true;
		}
		catch (Exception exception) {
			return false;
		}
	}

	private boolean failedForSyncing(Future<RecurringHolidayMutationResult> request) {
		try {
			request.get(10, TimeUnit.SECONDS);
			return false;
		}
		catch (ExecutionException exception) {
			return exception.getCause() instanceof ScheduleException scheduleException
				&& scheduleException.code()
					.equals(ExceptionCode.SCHEDULE_CONFIG_SYNC_IN_PROGRESS.code());
		}
		catch (Exception exception) {
			return false;
		}
	}

	private RecurringHolidayRuleCommand command(
		DayOfWeek dayOfWeek,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		long expectedConfigVersion,
		String changeReason
	) {
		return new RecurringHolidayRuleCommand(
			dayOfWeek,
			effectiveFrom,
			effectiveTo,
			"정기 휴일",
			expectedConfigVersion,
			"schedule-admin",
			changeReason);
	}

	private void createTemplateTimeSlotWithActiveReservation() {
		final RegularScheduleTemplate template = templateRepository.saveAndFlush(
			RegularScheduleTemplate.create(
				DayOfWeek.MONDAY,
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				8,
				4,
				validClassCapacities(),
				"schedule-admin"));
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, source, template_id,
				total_capacity, round_arena_capacity, class_capacity_json
			) VALUES ('2026-07-27', '09:00:00', 'TEMPLATE', ?, 8, 4, ?)
			""", template.getId(), CLASS_CAPACITIES_JSON);
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('r05-impact-member', '영향 회원', '010-0000-0000')
			""");
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status,
				payment_source, payment_due_at, approval_requested_at
			)
			SELECT id, 'FIRST_RIDE', '2026-07-27', '09:00:00',
				'pending_payment', 'single_payment',
				'2026-07-26 12:00:00', '2026-07-26 10:00:00'
			FROM members
			WHERE auth_subject = 'r05-impact-member'
			""");
	}

	private void createManualTimeSlot() {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES ('2026-07-27', '10:00:00', 6, 3, ?)
			""", CLASS_CAPACITIES_JSON);
	}

	private Map<String, Integer> validClassCapacities() {
		return Map.of(
			"FIRST_RIDE", 2,
			"ROUND_BEGINNER", 2,
			"ROUND_TROT", 2,
			"LARGE_ARENA_BEGINNER", 3,
			"LARGE_ARENA_TROT", 3,
			"CANTER_BEGINNER", 3,
			"CANTER", 3,
			"DRESSAGE", 1,
			"JUMPING", 1);
	}

	private void assertTemplateAndManualTimeSlotUnchanged() {
		assertThat(templateRepository.count()).isOne();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM time_slot_capacities
			WHERE source = 'MANUAL'
			  AND admin_closed = FALSE
			  AND recurring_holiday_closed = FALSE
			  AND template_inactive_closed = FALSE
			  AND total_capacity = 6
			  AND round_arena_capacity = 3
			""", Long.class)).isOne();
	}

	private void assertManualTimeSlotUnchanged() {
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM time_slot_capacities
			WHERE source = 'MANUAL'
			  AND admin_closed = FALSE
			  AND recurring_holiday_closed = FALSE
			  AND template_inactive_closed = FALSE
			""", Long.class)).isOne();
	}

	private void resetGuardActive(long activeVersion) {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = ?,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""", activeVersion);
	}

	private void assertGuard(String status, long activeVersion, Long pendingVersion) {
		final Map<String, Object> guard = jdbcTemplate.queryForMap("""
			SELECT status, active_version, pending_version
			FROM schedule_config_guard
			WHERE id = 1
			""");
		assertThat(guard.get("status")).isEqualTo(status);
		assertThat(((Number)guard.get("active_version")).longValue()).isEqualTo(activeVersion);
		if (pendingVersion == null) {
			assertThat(guard.get("pending_version")).isNull();
		}
		else {
			assertThat(((Number)guard.get("pending_version")).longValue()).isEqualTo(pendingVersion);
		}
	}

	private void assertAudit(long ruleId, String action, long pendingVersion) {
		final List<ScheduleAuditLog> auditLogs = auditLogRepository.findAllByTarget(
			ScheduleAuditTargetType.RECURRING_HOLIDAY,
			"recurring-holiday:" + ruleId);
		assertThat(auditLogs).hasSize(1);
		assertThat(auditLogs.getFirst().getAction()).isEqualTo(action);
		assertThat(((Number)auditLogs.getFirst().getMetadata()
			.get("pendingConfigVersion")).longValue()).isEqualTo(pendingVersion);
	}

	private void assertScheduleException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ScheduleException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
