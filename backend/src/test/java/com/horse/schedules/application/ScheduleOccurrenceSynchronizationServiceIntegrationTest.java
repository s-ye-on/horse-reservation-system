package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.horse.TestcontainersConfiguration;
import com.horse.schedules.domain.RecurringHolidayRule;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.domain.ScheduleDateStatus;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotSource;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
	"schedule.occurrence.long-running-threshold=PT30M",
	"schedule.occurrence.recovery-initial-delay-ms=3600000"
})
class ScheduleOccurrenceSynchronizationServiceIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);
	private static final long ACTIVE_VERSION = 1L;
	private static final long PENDING_VERSION = 2L;

	@Autowired
	ScheduleOccurrenceSynchronizationService synchronizationService;

	@MockitoSpyBean
	ScheduleSynchronizationStateService stateService;

	@MockitoSpyBean
	ScheduleOccurrenceDateSynchronizer dateSynchronizer;

	@Autowired
	ScheduleDateHorizonService horizonService;

	@Autowired
	ScheduleDateRepository scheduleDateRepository;

	@Autowired
	RegularScheduleTemplateRepository templateRepository;

	@Autowired
	RecurringHolidayRuleRepository holidayRepository;

	@Autowired
	TimeSlotCapacityRepository timeSlotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 동기화_fixture를_초기화한다() {
		given(clock.getZone()).willReturn(SEOUL_ZONE);
		given(clock.instant()).willReturn(NOW);
		reset(dateSynchronizer);
		jdbcTemplate.update("DELETE FROM schedule_audit_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM regular_schedule_templates");
		jdbcTemplate.update("DELETE FROM recurring_holiday_rules");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
		resetGuardActive(ACTIVE_VERSION);
		horizonService.ensureHorizon();
		beginSynchronization(PENDING_VERSION, LocalDateTime.of(2026, 7, 24, 9, 30));
	}

	@Test
	void 누락_horizon을_보충하고_정기_휴일을_제외한_정규_occurrence를_생성한다() {
		createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 8);
		createTemplate(DayOfWeek.MONDAY, LocalTime.of(9, 0), 8);
		createMondayHoliday();
		final TimeSlotCapacity manual = timeSlotRepository.saveAndFlush(
			TimeSlotCapacity.create(
				TODAY,
				LocalTime.of(10, 0),
				5,
				2,
				validClassCapacities()));
		final LocalDate horizonEnd = TODAY.plusMonths(3);
		jdbcTemplate.update(
			"DELETE FROM schedule_dates WHERE schedule_date = ?",
			horizonEnd);

		final ScheduleOccurrenceSynchronizationResult result =
			synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		assertThat(result.status().status()).isEqualTo(ScheduleConfigStatus.ACTIVE);
		assertThat(result.status().activeVersion()).isEqualTo(PENDING_VERSION);
		assertThat(scheduleDateRepository.findByScheduleDate(horizonEnd)).isPresent();
		assertThat(result.status().totalDateCount()).isEqualTo(expectedHorizonDateCount());
		assertThat(result.status().appliedDateCount()).isEqualTo(expectedHorizonDateCount());
		assertThat(countTemplateSlots(DayOfWeek.FRIDAY)).isPositive();
		assertThat(countTemplateSlots(DayOfWeek.MONDAY)).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM time_slot_capacities
			WHERE source = 'TEMPLATE' AND capacity_overridden = TRUE
			""", Integer.class)).isZero();
		final TimeSlotCapacity preservedManual = timeSlotRepository.findById(manual.getId())
			.orElseThrow();
		assertThat(preservedManual.getSource()).isEqualTo(TimeSlotSource.MANUAL);
		assertThat(preservedManual.isCapacityOverridden()).isTrue();
		assertThat(preservedManual.getTotalCapacity()).isEqualTo(5);
	}

	@Test
	void OPEN_날짜는_정기_휴일을_우회해_정규_occurrence를_생성한다() {
		createTemplate(DayOfWeek.MONDAY, LocalTime.of(9, 0), 8);
		createMondayHoliday();
		final LocalDate monday = LocalDate.of(2026, 7, 27);
		jdbcTemplate.update(
			"UPDATE schedule_dates SET status = 'OPEN' WHERE schedule_date = ?",
			monday);

		synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		final TimeSlotCapacity timeSlot = timeSlotRepository.findByLessonDateAndStartTime(
			monday,
			LocalTime.of(9, 0)).orElseThrow();
		assertThat(timeSlot.getSource()).isEqualTo(TimeSlotSource.TEMPLATE);
		assertThat(timeSlot.isRecurringHolidayClosed()).isFalse();
		assertThat(timeSlot.isClosed()).isFalse();
	}

	@Test
	void 미래_TEMPLATE_자동_원인과_상속_정원을_동기화하고_관리자_마감은_보존한다() {
		final RegularScheduleTemplate activeTemplate =
			createTemplate(DayOfWeek.FRIDAY, LocalTime.of(11, 0), 8);
		final RegularScheduleTemplate inactiveTemplate =
			createTemplate(DayOfWeek.FRIDAY, LocalTime.of(12, 0), 8);
		inactiveTemplate.deactivate("schedule-admin");
		templateRepository.saveAndFlush(inactiveTemplate);
		final TimeSlotCapacity activeSlot = TimeSlotCapacity.createFromTemplate(
			TODAY,
			activeTemplate.getId(),
			LocalTime.of(11, 0),
			LocalTime.of(11, 45),
			5,
			2,
			validClassCapacities(),
			true);
		activeSlot.changeAdminClosed(true);
		final TimeSlotCapacity inactiveSlot = TimeSlotCapacity.createFromTemplate(
			TODAY,
			inactiveTemplate.getId(),
			LocalTime.of(12, 0),
			LocalTime.of(12, 45),
			5,
			2,
			validClassCapacities(),
			false);
		timeSlotRepository.saveAllAndFlush(List.of(activeSlot, inactiveSlot));

		synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		final TimeSlotCapacity synchronizedActive = timeSlotRepository.findById(activeSlot.getId())
			.orElseThrow();
		assertThat(synchronizedActive.isAdminClosed()).isTrue();
		assertThat(synchronizedActive.isRecurringHolidayClosed()).isFalse();
		assertThat(synchronizedActive.isTemplateInactiveClosed()).isFalse();
		assertThat(synchronizedActive.getTotalCapacity()).isEqualTo(8);
		assertThat(synchronizedActive.getRoundArenaCapacity()).isEqualTo(4);
		assertThat(synchronizedActive.getClassCapacities()).isEqualTo(validClassCapacities());
		assertThat(synchronizedActive.isCapacityOverridden()).isFalse();
		final TimeSlotCapacity synchronizedInactive =
			timeSlotRepository.findById(inactiveSlot.getId()).orElseThrow();
		assertThat(synchronizedInactive.isTemplateInactiveClosed()).isTrue();
		assertThat(synchronizedInactive.isClosed()).isTrue();
		assertThat(synchronizedInactive.isCapacityOverridden()).isFalse();
	}

	@Test
	void 일부_날짜_적용_후_같은_version으로_재시작하면_나머지만_적용한다() {
		createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 8);
		stateService.prepare(PENDING_VERSION);
		final ScheduleOccurrenceDateResult first =
			dateSynchronizer.synchronize(PENDING_VERSION, TODAY);
		final ScheduleOccurrenceDateResult repeated =
			dateSynchronizer.synchronize(PENDING_VERSION, TODAY);

		final ScheduleOccurrenceSynchronizationResult result =
			synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		assertThat(first.applied()).isTrue();
		assertThat(repeated.skipped()).isTrue();
		assertThat(result.skippedDateCount()).isPositive();
		assertThat(result.status().status()).isEqualTo(ScheduleConfigStatus.ACTIVE);
		assertThat(countSlots(TODAY, LocalTime.of(9, 0))).hasSize(1);
	}

	@Test
	void 하루가_지나면_ACTIVE_version으로_새_horizon_마지막_날짜를_보충한다() {
		createTemplate(DayOfWeek.SUNDAY, LocalTime.of(9, 0), 8);
		synchronizationService.retryPendingSynchronization(PENDING_VERSION);
		given(clock.instant()).willReturn(NOW.plus(1, ChronoUnit.DAYS));
		final LocalDate newHorizonEnd = TODAY.plusDays(1).plusMonths(3);

		final ScheduleOccurrenceSynchronizationResult result =
			synchronizationService.synchronizeCurrentHorizon();

		assertThat(result.status().status()).isEqualTo(ScheduleConfigStatus.ACTIVE);
		assertThat(scheduleDateRepository.findByScheduleDate(newHorizonEnd)).isPresent();
		assertThat(timeSlotRepository.findByLessonDateAndStartTime(
			newHorizonEnd,
			LocalTime.of(9, 0))).isPresent();
	}

	@Test
	void 기존_미래_상속_슬롯은_새_SYNCING_version에서만_정원을_조정한다() {
		final RegularScheduleTemplate template =
			createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 8);
		synchronizationService.retryPendingSynchronization(PENDING_VERSION);
		final LocalDate inheritedDate = LocalDate.of(2026, 7, 31);
		final LocalDate overriddenDate = LocalDate.of(2026, 8, 7);
		final LocalDate closedDate = LocalDate.of(2026, 8, 14);
		final LocalDate pastDate = LocalDate.of(2026, 7, 17);
		final String staleClasses = """
			{"FIRST_RIDE":1,"ROUND_BEGINNER":2,"ROUND_TROT":2,
			"LARGE_ARENA_BEGINNER":3,"LARGE_ARENA_TROT":3,
			"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
			""";
		jdbcTemplate.update("""
			UPDATE time_slot_capacities
			SET total_capacity = 5, round_arena_capacity = 2,
			    class_capacity_json = ?, admin_closed = TRUE
			WHERE lesson_date = ? AND start_time = '09:00:00'
			""", staleClasses, inheritedDate);
		jdbcTemplate.update("""
			UPDATE time_slot_capacities
			SET total_capacity = 5, round_arena_capacity = 2,
			    capacity_overridden = TRUE
			WHERE lesson_date = ? AND start_time = '09:00:00'
			""", overriddenDate);
		jdbcTemplate.update("""
			UPDATE time_slot_capacities
			SET total_capacity = 5, round_arena_capacity = 2,
			    recurring_holiday_closed = TRUE,
			    template_inactive_closed = TRUE
			WHERE lesson_date = ? AND start_time = '09:00:00'
			""", closedDate);
		jdbcTemplate.update("""
			UPDATE time_slot_capacities
			SET total_capacity = 5, round_arena_capacity = 2
			WHERE lesson_date = ? AND start_time = '09:00:00'
			""", TODAY);
		jdbcTemplate.update(
			"UPDATE schedule_dates SET status = 'CLOSED' WHERE schedule_date = ?", closedDate);
		final TimeSlotCapacity past = timeSlotRepository.saveAndFlush(
			TimeSlotCapacity.createFromTemplate(
				pastDate, template.getId(), LocalTime.of(9, 0), LocalTime.of(9, 45),
				5, 2, validClassCapacities(), false));
		final TimeSlotCapacity manual = timeSlotRepository.saveAndFlush(
			TimeSlotCapacity.create(
				inheritedDate, LocalTime.of(11, 0), 5, 2, validClassCapacities()));

		final ScheduleOccurrenceSynchronizationResult result =
			synchronizationService.synchronizeCurrentHorizon();

		assertThat(result.targetVersion()).isEqualTo(PENDING_VERSION + 1);
		assertThat(result.status().status()).isEqualTo(ScheduleConfigStatus.ACTIVE);
		assertThat(result.status().activeVersion()).isEqualTo(PENDING_VERSION + 1);
		final TimeSlotCapacity inherited = timeSlotRepository.findByLessonDateAndStartTime(
			inheritedDate, LocalTime.of(9, 0)).orElseThrow();
		assertThat(inherited.getTotalCapacity()).isEqualTo(8);
		assertThat(inherited.getRoundArenaCapacity()).isEqualTo(4);
		assertThat(inherited.getClassCapacities()).isEqualTo(validClassCapacities());
		assertThat(inherited.isCapacityOverridden()).isFalse();
		assertThat(inherited.isAdminClosed()).isTrue();
		assertThat(timeSlotRepository.findByLessonDateAndStartTime(
			overriddenDate, LocalTime.of(9, 0)).orElseThrow().getTotalCapacity()).isEqualTo(5);
		final TimeSlotCapacity closed = timeSlotRepository.findByLessonDateAndStartTime(
			closedDate, LocalTime.of(9, 0)).orElseThrow();
		assertThat(closed.getTotalCapacity()).isEqualTo(8);
		assertThat(closed.isRecurringHolidayClosed()).isTrue();
		assertThat(closed.isTemplateInactiveClosed()).isFalse();
		assertThat(scheduleDateRepository.findByScheduleDate(closedDate).orElseThrow()
			.getStatus()).isEqualTo(ScheduleDateStatus.CLOSED);
		assertThat(timeSlotRepository.findById(past.getId()).orElseThrow()
			.getTotalCapacity()).isEqualTo(5);
		assertThat(timeSlotRepository.findByLessonDateAndStartTime(
			TODAY, LocalTime.of(9, 0)).orElseThrow().getTotalCapacity()).isEqualTo(5);
		assertThat(timeSlotRepository.findById(manual.getId()).orElseThrow()
			.getTotalCapacity()).isEqualTo(5);
	}

	@Test
	void 동시_재시도는_같은_occurrence를_중복_생성하지_않는다() throws Exception {
		createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 8);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<ScheduleOccurrenceSynchronizationResult> first =
				executor.submit(() -> retryAfterSignal(ready, start));
			final Future<ScheduleOccurrenceSynchronizationResult> second =
				executor.submit(() -> retryAfterSignal(ready, start));
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(first.get(20, TimeUnit.SECONDS).status().status())
				.isEqualTo(ScheduleConfigStatus.ACTIVE);
			assertThat(second.get(20, TimeUnit.SECONDS).status().status())
				.isEqualTo(ScheduleConfigStatus.ACTIVE);
		}
		assertThat(countSlots(TODAY, LocalTime.of(9, 0))).hasSize(1);
		assertThat(synchronizationService.getStatus().activeVersion())
			.isEqualTo(PENDING_VERSION);
	}

	@Test
	void 날짜_동기화가_실패하면_SYNCING과_비식별_실패_정보를_유지한다(
		CapturedOutput output
	) {
		doThrow(new IllegalStateException("sensitive failure"))
			.when(dateSynchronizer)
			.synchronize(eq(PENDING_VERSION), eq(TODAY));

		assertThatThrownBy(() ->
			synchronizationService.retryPendingSynchronization(PENDING_VERSION))
			.isInstanceOf(IllegalStateException.class);

		final ScheduleSynchronizationStatus status = synchronizationService.getStatus();
		assertThat(status.status()).isEqualTo(ScheduleConfigStatus.SYNCING);
		assertThat(status.pendingVersion()).isEqualTo(PENDING_VERSION);
		assertThat(status.lastFailureCode()).isEqualTo("SCHEDULE_OCCURRENCE_SYNC_FAILED");
		assertThat(status.lastFailureSummary())
			.isEqualTo("pending schedule synchronization failed");
		assertThat(status.lastFailureSummary()).doesNotContain("sensitive");
		assertThat(output)
			.contains("schedule_sync_failed pendingVersion=2")
			.contains("failureCode=SCHEDULE_OCCURRENCE_SYNC_FAILED")
			.contains("appliedDateCount=0")
			.contains("totalDateCount=" + expectedHorizonDateCount())
			.doesNotContain("sensitive failure");
	}

	@Test
	void 준비_단계가_실패해도_SYNCING과_비식별_실패_정보를_유지한다() {
		doThrow(new IllegalStateException("sensitive prepare failure"))
			.when(stateService)
			.prepare(PENDING_VERSION);

		assertThatThrownBy(() ->
			synchronizationService.retryPendingSynchronization(PENDING_VERSION))
			.isInstanceOf(IllegalStateException.class);

		final ScheduleSynchronizationStatus status = synchronizationService.getStatus();
		assertThat(status.status()).isEqualTo(ScheduleConfigStatus.SYNCING);
		assertThat(status.lastFailureCode()).isEqualTo("SCHEDULE_OCCURRENCE_SYNC_FAILED");
		assertThat(status.lastFailureSummary())
			.isEqualTo("pending schedule synchronization failed");
		assertThat(status.lastFailureSummary()).doesNotContain("sensitive");
	}

	@Test
	void 이전_pending_version_worker는_설정_version_충돌로_중단한다() {
		resetGuardActive(PENDING_VERSION + 1);

		assertThatThrownBy(() ->
			synchronizationService.retryPendingSynchronization(PENDING_VERSION))
			.isInstanceOfSatisfying(
				ScheduleException.class,
				exception -> assertThat(exception.code())
					.isEqualTo("SCHEDULE_CONFIG_VERSION_CONFLICT"));
	}

	@Test
	void 진행률_목표_건수는_누락된_horizon_행과_무관하게_계산한다() {
		jdbcTemplate.update(
			"DELETE FROM schedule_dates WHERE schedule_date = ?",
			TODAY.plusMonths(3));

		final ScheduleSynchronizationStatus status = synchronizationService.getStatus();

		assertThat(status.totalDateCount()).isEqualTo(expectedHorizonDateCount());
		assertThat(scheduleDateRepository.count()).isEqualTo(expectedHorizonDateCount() - 1);
	}

	@Test
	void 설정된_기준을_넘긴_SYNCING은_장기_진행으로_표시한다() {
		beginSynchronization(
			PENDING_VERSION,
			LocalDateTime.of(2026, 7, 24, 9, 0));

		final ScheduleSynchronizationStatus status = synchronizationService.getStatus();

		assertThat(status.longRunning()).isTrue();
		assertThat(status.pendingVersion()).isEqualTo(PENDING_VERSION);
		assertThat(status.totalDateCount()).isEqualTo(expectedHorizonDateCount());
		assertThat(status.appliedDateCount()).isZero();
	}

	@Test
	void CLOSING과_CLOSED_날짜는_occurrence를_생성하지_않고_version만_적용한다() {
		createTemplate(DayOfWeek.FRIDAY, LocalTime.of(9, 0), 8);
		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'CLOSING', resume_status = 'NORMAL'
			WHERE schedule_date = ?
			""", TODAY);
		final LocalDate saturday = TODAY.plusDays(1);
		jdbcTemplate.update(
			"UPDATE schedule_dates SET status = 'CLOSED' WHERE schedule_date = ?",
			saturday);

		synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		assertThat(timeSlotRepository.findByLessonDateAndStartTime(
			TODAY,
			LocalTime.of(9, 0))).isEmpty();
		assertThat(scheduleDateRepository.findByScheduleDate(TODAY).orElseThrow()
			.getAppliedConfigVersion()).isEqualTo(PENDING_VERSION);
		assertThat(scheduleDateRepository.findByScheduleDate(saturday).orElseThrow()
			.getAppliedConfigVersion()).isEqualTo(PENDING_VERSION);
	}

	@Test
	void 정기_휴일로_닫힌_TEMPLATE의_활성_예약을_유지하고_ACTIVE로_완료한다() {
		final RegularScheduleTemplate template =
			createTemplate(DayOfWeek.MONDAY, LocalTime.of(9, 0), 8);
		createMondayHoliday();
		final LocalDate monday = LocalDate.of(2026, 7, 27);
		final TimeSlotCapacity timeSlot = timeSlotRepository.saveAndFlush(
			TimeSlotCapacity.createFromTemplate(
				monday,
				template.getId(),
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				8,
				4,
				validClassCapacities(),
				false));
		final long memberId = insertMember();
		final long reservationId = insertActiveReservation(memberId, monday);

		final ScheduleOccurrenceSynchronizationResult result =
			synchronizationService.retryPendingSynchronization(PENDING_VERSION);

		assertThat(result.status().status()).isEqualTo(ScheduleConfigStatus.ACTIVE);
		assertThat(timeSlotRepository.findById(timeSlot.getId()).orElseThrow()
			.isRecurringHolidayClosed()).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?",
			String.class,
			reservationId)).isEqualTo("pending_payment");
	}

	private ScheduleOccurrenceSynchronizationResult retryAfterSignal(
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
		return synchronizationService.retryPendingSynchronization(PENDING_VERSION);
	}

	private RegularScheduleTemplate createTemplate(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		int totalCapacity
	) {
		return templateRepository.saveAndFlush(RegularScheduleTemplate.create(
			dayOfWeek,
			startTime,
			startTime.plusMinutes(45),
			totalCapacity,
			Math.min(totalCapacity, 4),
			validClassCapacities(),
			"schedule-admin"));
	}

	private void createMondayHoliday() {
		holidayRepository.saveAndFlush(RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			LocalDate.of(1970, 1, 1),
			null,
			"기본 월요일 휴무",
			"schedule-admin"));
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

	private long countTemplateSlots(DayOfWeek dayOfWeek) {
		return timeSlotRepository.findAll().stream()
			.filter(timeSlot -> timeSlot.getSource() == TimeSlotSource.TEMPLATE)
			.filter(timeSlot -> timeSlot.getLessonDate().getDayOfWeek() == dayOfWeek)
			.count();
	}

	private long expectedHorizonDateCount() {
		return TODAY.datesUntil(TODAY.plusMonths(3).plusDays(1)).count();
	}

	private long insertMember() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('r06-member', 'R06 회원', '010-0000-0000')
			""");
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = 'r06-member'",
			Long.class);
	}

	private long insertActiveReservation(long memberId, LocalDate lessonDate) {
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
			) VALUES (?, 'ROUND_BEGINNER', ?, '09:00:00', 'pending_payment',
				'single_payment', '2026-07-24 12:00:00', '2026-07-24 09:00:00')
			""", memberId, lessonDate);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM reservations WHERE member_id = ? AND lesson_date = ?",
			Long.class,
			memberId,
			lessonDate);
	}

	private List<TimeSlotCapacity> countSlots(LocalDate date, LocalTime startTime) {
		return timeSlotRepository.findAll().stream()
			.filter(timeSlot -> timeSlot.getLessonDate().equals(date))
			.filter(timeSlot -> timeSlot.getStartTime().equals(startTime))
			.toList();
	}

	private void resetGuardActive(long activeVersion) {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET
				status = 'ACTIVE',
				active_version = ?,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL,
				last_completed_at = NULL,
				last_failed_at = NULL,
				last_failure_code = NULL,
				last_failure_summary = NULL,
				version = 0
			WHERE id = 1
			""", activeVersion);
	}

	private void beginSynchronization(long pendingVersion, LocalDateTime startedAt) {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET
				status = 'SYNCING',
				active_version = ?,
				pending_version = ?,
				sync_started_at = ?,
				sync_started_by = 'schedule-admin',
				last_completed_at = NULL,
				last_failed_at = NULL,
				last_failure_code = NULL,
				last_failure_summary = NULL
			WHERE id = 1
			""", ACTIVE_VERSION, pendingVersion, startedAt);
	}
}
