package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
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
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.RecurringHolidayRule;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegularScheduleTemplateServiceIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
	private static final String CLASS_CAPACITIES_JSON = """
		{
			"FIRST_RIDE": 2,
			"ROUND_BEGINNER": 2,
			"ROUND_TROT": 2,
			"LARGE_ARENA_BEGINNER": 3,
			"LARGE_ARENA_TROT": 3,
			"DRESSAGE": 1,
			"JUMPING": 1
		}
		""";

	@Autowired
	RegularScheduleTemplateService templateService;

	@Autowired
	RegularScheduleTemplateRepository templateRepository;

	@Autowired
	RecurringHolidayRuleRepository holidayRepository;

	@Autowired
	ScheduleAuditLogRepository auditLogRepository;

	@Autowired
	ScheduleDateHorizonService horizonService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 일정_설정_fixture를_초기화한다() {
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
	void 월요일_템플릿을_생성하고_SYNCING과_감사를_같이_반영한다() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			java.time.LocalDate.of(2026, 7, 1),
			null,
			"월요일 정기 휴일",
			"schedule-admin"));
		createManualTimeSlot();

		final ScheduleTemplateMutationResult result = templateService.create(
			command(DayOfWeek.MONDAY, LocalTime.of(9, 0), 1L, "월요일 정규 수업"));

		assertThat(result.template().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(result.template().endTime()).isEqualTo(LocalTime.of(9, 45));
		assertThat(result.pendingConfigVersion()).isEqualTo(2L);
		assertThat(result.impact().affectedDateCount()).isPositive();
		assertThat(result.impact().existingTimeSlotCount()).isZero();
		assertThat(holidayRepository.count()).isOne();
		assertGuard("SYNCING", 1L, 2L);
		assertAudit(result.template().id(), "CREATED", 2L);
		assertManualTimeSlotUnchanged();
	}

	@Test
	void stale_설정_version은_변경_전에_거부한다() {
		assertScheduleException(
			() -> templateService.create(
				command(DayOfWeek.TUESDAY, LocalTime.of(9, 0), 2L, "stale 요청")),
			ExceptionCode.SCHEDULE_CONFIG_VERSION_CONFLICT);

		assertThat(templateRepository.count()).isZero();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM schedule_audit_logs",
			Long.class)).isZero();
		assertGuard("ACTIVE", 1L, null);
	}

	@Test
	void SYNCING_중_추가_템플릿_변경은_거부한다() {
		templateService.create(
			command(DayOfWeek.TUESDAY, LocalTime.of(9, 0), 1L, "첫 변경"));

		assertScheduleException(
			() -> templateService.create(
				command(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), 1L, "추가 변경")),
			ExceptionCode.SCHEDULE_CONFIG_SYNC_IN_PROGRESS);

		assertThat(templateRepository.count()).isOne();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM schedule_audit_logs",
			Long.class)).isOne();
	}

	@Test
	void 같은_요일과_시작_시각의_템플릿은_중복_생성하지_않는다() {
		templateRepository.saveAndFlush(createTemplate(
			DayOfWeek.TUESDAY,
			LocalTime.of(9, 0)));

		assertScheduleException(
			() -> templateService.create(
				command(DayOfWeek.TUESDAY, LocalTime.of(9, 0), 1L, "중복 생성")),
			ExceptionCode.SCHEDULE_TEMPLATE_ALREADY_EXISTS);

		assertGuard("ACTIVE", 1L, null);
		assertThat(templateRepository.count()).isOne();
	}

	@Test
	void 템플릿을_변경하고_비활성화한_뒤_다시_활성화한다() {
		final RegularScheduleTemplate template = templateRepository.saveAndFlush(
			createTemplate(DayOfWeek.TUESDAY, LocalTime.of(9, 0)));
		createManualTimeSlot();

		final ScheduleTemplateMutationResult updated = templateService.update(
			template.getId(),
			command(DayOfWeek.WEDNESDAY, LocalTime.of(13, 30), 1L, "시간표 변경"));
		assertThat(updated.template().dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
		assertThat(updated.template().startTime()).isEqualTo(LocalTime.of(13, 30));
		assertThat(updated.template().endTime()).isEqualTo(LocalTime.of(14, 15));
		assertThat(updated.impact().affectedDateCount()).isGreaterThan(20);

		resetGuardActive(2L);
		final ScheduleTemplateMutationResult deactivated = templateService.deactivate(
			template.getId(),
			2L,
			"schedule-admin",
			"운영 중단");
		assertThat(deactivated.template().active()).isFalse();

		resetGuardActive(3L);
		final ScheduleTemplateMutationResult activated = templateService.activate(
			template.getId(),
			3L,
			"schedule-admin",
			"운영 재개");
		assertThat(activated.template().active()).isTrue();
		assertThat(auditLogRepository.findAllByTarget(
			ScheduleAuditTargetType.TEMPLATE,
			"template:" + template.getId()))
			.extracting(ScheduleAuditLog::getAction)
			.containsExactly("UPDATED", "DEACTIVATED", "ACTIVATED");
		assertManualTimeSlotUnchanged();
	}

	@Test
	void 영향_미리보기는_기존_TEMPLATE_시간대와_활성_예약을_집계한다() {
		final RegularScheduleTemplate template = templateRepository.saveAndFlush(
			createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0)));
		insertTemplateTimeSlot(template.getId());
		insertActiveReservation();

		final ScheduleTemplateImpactPreview preview = templateService.preview(
			DayOfWeek.FRIDAY,
			LocalTime.of(9, 0),
			template.getId());

		assertThat(preview.affectedDateCount()).isPositive();
		assertThat(preview.existingTimeSlotCount()).isOne();
		assertThat(preview.activeReservationCount()).isOne();
	}

	@Test
	void 신규_템플릿_미리보기는_같은_요일과_시각의_MANUAL_슬롯을_집계한다() {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES ('2026-07-31', '11:00:00', 6, 3, ?)
			""", CLASS_CAPACITIES_JSON);

		final ScheduleTemplateImpactPreview preview = templateService.preview(
			DayOfWeek.FRIDAY,
			LocalTime.of(11, 0),
			null);

		assertThat(preview.affectedDateCount()).isPositive();
		assertThat(preview.existingTimeSlotCount()).isOne();
		assertThat(preview.activeReservationCount()).isZero();
	}

	@Test
	void 동시_설정_변경은_하나만_성공한다() throws Exception {
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<ScheduleTemplateMutationResult> first = executor.submit(
				() -> createAfterSignal(
					ready,
					start,
					command(DayOfWeek.THURSDAY, LocalTime.of(9, 0), 1L, "동시 변경 1")));
			final Future<ScheduleTemplateMutationResult> second = executor.submit(
				() -> createAfterSignal(
					ready,
					start,
					command(DayOfWeek.FRIDAY, LocalTime.of(10, 0), 1L, "동시 변경 2")));
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final List<Future<ScheduleTemplateMutationResult>> requests = List.of(first, second);
			assertThat(requests.stream().filter(this::completedSuccessfully).count()).isOne();
			assertThat(requests.stream().filter(this::failedForSyncing).count()).isOne();
			assertThat(templateRepository.count()).isOne();
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM schedule_audit_logs",
				Long.class)).isOne();
		}
	}

	@Test
	void 동시_변경과_비활성화도_하나만_성공한다() throws Exception {
		final RegularScheduleTemplate template = templateRepository.saveAndFlush(
			createTemplate(DayOfWeek.TUESDAY, LocalTime.of(9, 0)));
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<ScheduleTemplateMutationResult> update = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				return templateService.update(
					template.getId(),
					command(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), 1L, "동시 변경"));
			});
			final Future<ScheduleTemplateMutationResult> deactivate = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				return templateService.deactivate(
					template.getId(),
					1L,
					"schedule-admin",
					"동시 비활성화");
			});
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final List<Future<ScheduleTemplateMutationResult>> requests = List.of(update, deactivate);
			assertThat(requests.stream().filter(this::completedSuccessfully).count()).isOne();
			assertThat(requests.stream().filter(this::failedForSyncing).count()).isOne();
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM schedule_audit_logs",
				Long.class)).isOne();
		}
	}

	private ScheduleTemplateMutationResult createAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		RegularScheduleTemplateCommand command
	) throws InterruptedException {
		ready.countDown();
		assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
		return templateService.create(command);
	}

	private boolean completedSuccessfully(Future<ScheduleTemplateMutationResult> request) {
		try {
			request.get(10, TimeUnit.SECONDS);
			return true;
		}
		catch (Exception exception) {
			return false;
		}
	}

	private boolean failedForSyncing(Future<ScheduleTemplateMutationResult> request) {
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

	private RegularScheduleTemplateCommand command(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		long expectedConfigVersion,
		String reason
	) {
		return new RegularScheduleTemplateCommand(
			dayOfWeek,
			startTime,
			startTime.plusMinutes(45),
			8,
			4,
			validClassCapacities(),
			expectedConfigVersion,
			"schedule-admin",
			reason);
	}

	private RegularScheduleTemplate createTemplate(DayOfWeek dayOfWeek, LocalTime startTime) {
		return RegularScheduleTemplate.create(
			dayOfWeek,
			startTime,
			startTime.plusMinutes(45),
			8,
			4,
			validClassCapacities(),
			"schedule-admin");
	}

	private Map<String, Integer> validClassCapacities() {
		return Map.of(
			"FIRST_RIDE", 2,
			"ROUND_BEGINNER", 2,
			"ROUND_TROT", 2,
			"LARGE_ARENA_BEGINNER", 3,
			"LARGE_ARENA_TROT", 3,
			"DRESSAGE", 1,
			"JUMPING", 1);
	}

	private void createManualTimeSlot() {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES ('2026-08-01', '11:00:00', 6, 3, ?)
			""", CLASS_CAPACITIES_JSON);
	}

	private void assertManualTimeSlotUnchanged() {
		final Map<String, Object> timeSlot = jdbcTemplate.queryForMap("""
			SELECT source, template_id, total_capacity, round_arena_capacity,
				admin_closed, recurring_holiday_closed, template_inactive_closed
			FROM time_slot_capacities
			WHERE lesson_date = '2026-08-01'
			  AND start_time = '11:00:00'
			""");
		assertThat(timeSlot.get("source")).isEqualTo("MANUAL");
		assertThat(timeSlot.get("template_id")).isNull();
		assertThat(((Number)timeSlot.get("total_capacity")).intValue()).isEqualTo(6);
		assertThat(((Number)timeSlot.get("round_arena_capacity")).intValue()).isEqualTo(3);
		assertThat(timeSlot.get("admin_closed")).isEqualTo(false);
		assertThat(timeSlot.get("recurring_holiday_closed")).isEqualTo(false);
		assertThat(timeSlot.get("template_inactive_closed")).isEqualTo(false);
	}

	private void insertTemplateTimeSlot(long templateId) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, source, template_id,
				total_capacity, round_arena_capacity, class_capacity_json
			) VALUES ('2026-07-31', '09:00:00', 'TEMPLATE', ?, 8, 4, ?)
			""", templateId, CLASS_CAPACITIES_JSON);
	}

	private void insertActiveReservation() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('r04-impact-member', '영향 회원', '010-0000-0000')
			""");
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status,
				payment_source, payment_due_at, approval_requested_at
			)
			SELECT id, 'FIRST_RIDE', '2026-07-31', '09:00:00',
				'pending_payment', 'single_payment',
				'2026-07-30 12:00:00', '2026-07-30 10:00:00'
			FROM members
			WHERE auth_subject = 'r04-impact-member'
			""");
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

	private void assertAudit(long templateId, String action, long pendingVersion) {
		final List<ScheduleAuditLog> auditLogs = auditLogRepository.findAllByTarget(
			ScheduleAuditTargetType.TEMPLATE,
			"template:" + templateId);
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
