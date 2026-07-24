package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.application.ReservationCapacityService;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.infrastructure.ReservationMemberDayGuardRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.application.AdminTimeSlotService;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimeSlotDeletionConcurrencyIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 21);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 7, 14, 10, 0);

	@Autowired
	AdminTimeSlotService adminTimeSlotService;

	@Autowired
	ReservationCapacityService reservationCapacityService;

	@Autowired
	TimeSlotCapacityRepository timeSlotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ScheduleConfigGuardRepository configGuardRepository;

	@Autowired
	ScheduleDateRepository scheduleDateRepository;

	@Autowired
	ReservationMemberDayGuardRepository memberDayGuardRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		given(clock.instant()).willReturn(Instant.parse("2026-07-14T01:00:00Z"));
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
		resetScheduleConfigGuard();
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
		createScheduleDate();
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

	@Test
	void 시간대_삭제와_예약_생성은_직렬화되어_고아_예약을_남기지_않는다() throws Exception {
		final TestData data = createTestData();
		final CountDownLatch startSignal = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			final Future<OperationResult> deletion = executor.submit(() -> deleteTimeSlot(startSignal, data.timeSlotId()));
			final Future<OperationResult> reservation = executor.submit(() -> reserve(startSignal, data));
			startSignal.countDown();

			final EnumSet<OperationResult> results = EnumSet.of(
				deletion.get(10, TimeUnit.SECONDS),
				reservation.get(10, TimeUnit.SECONDS));

			assertThat(results).containsExactlyInAnyOrder(OperationResult.SUCCEEDED, OperationResult.REJECTED);
			assertConsistentFinalState();
		}
		finally {
			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private OperationResult deleteTimeSlot(CountDownLatch startSignal, Long timeSlotId) throws InterruptedException {
		startSignal.await();
		try {
			adminTimeSlotService.deleteTimeSlot(timeSlotId);
			return OperationResult.SUCCEEDED;
		}
		catch (TimeSlotException exception) {
			return OperationResult.REJECTED;
		}
	}

	private OperationResult reserve(CountDownLatch startSignal, TestData data) throws InterruptedException {
		startSignal.await();
		try {
			transactionTemplate().executeWithoutResult(status -> {
				lockReservationContext(data.memberId());
				final TimeSlotCapacity timeSlot = reservationCapacityService.lockAndEnsureAvailable(
					data.timeSlotId(),
					data.memberId(),
					RidingClass.ROUND_BEGINNER);
				reservationCapacityService.createCouponReservation(
					timeSlot,
					data.memberId(),
					RidingClass.ROUND_BEGINNER,
					data.couponId(),
					REQUESTED_AT);
			});
			return OperationResult.SUCCEEDED;
		}
		catch (TimeSlotException exception) {
			return OperationResult.REJECTED;
		}
	}

	private void assertConsistentFinalState() {
		final int timeSlotCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM time_slot_capacities",
			Integer.class);
		final int reservationCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations",
			Integer.class);
		assertThat(timeSlotCount).isEqualTo(reservationCount);
		assertThat(timeSlotCount).isIn(0, 1);
	}

	private void lockReservationContext(Long memberId) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		guard.ensureActive();
		scheduleDateRepository.findByScheduleDateForUpdate(LESSON_DATE)
			.orElseThrow()
			.ensureAppliedConfigVersion(guard.getActiveVersion());
		memberDayGuardRepository.acquire(memberId, LESSON_DATE);
	}

	private void createScheduleDate() {
		final long activeVersion = jdbcTemplate.queryForObject(
			"SELECT active_version FROM schedule_config_guard WHERE id = ?",
			Long.class,
			ScheduleConfigGuard.SINGLETON_ID);
		scheduleDateRepository.saveAndFlush(ScheduleDate.create(LESSON_DATE, activeVersion));
	}

	private TransactionTemplate transactionTemplate() {
		return new TransactionTemplate(transactionManager);
	}

	private TestData createTestData() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('timeslot-delete-member', '삭제 경쟁 회원', '010-0000-0000')
			""");
		final Long memberId = jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = 'timeslot-delete-member'",
			Long.class);
		jdbcTemplate.update("""
			INSERT INTO coupons (member_id, coupon_type, total_count, remaining_count)
			VALUES (?, 'general', 10, 10)
			""", memberId);
		final Long couponId = jdbcTemplate.queryForObject(
			"SELECT id FROM coupons WHERE member_id = ?",
			Long.class,
			memberId);
		final Map<String, Integer> classCapacities = new HashMap<>();
		for (RidingClass ridingClass : RidingClass.values()) {
			classCapacities.put(ridingClass.name(), 8);
		}
		final TimeSlotCapacity timeSlot = timeSlotRepository.saveAndFlush(TimeSlotCapacity.create(
			LESSON_DATE,
			START_TIME,
			8,
			4,
			classCapacities));
		return new TestData(timeSlot.getId(), memberId, couponId);
	}

	private enum OperationResult {
		SUCCEEDED,
		REJECTED
	}

	private record TestData(Long timeSlotId, Long memberId, Long couponId) {
	}
}
