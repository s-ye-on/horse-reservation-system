package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduleDateHorizonServiceIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant FIRST_DAY = Instant.parse("2026-07-24T01:00:00Z");
	private static final Instant NEXT_DAY = Instant.parse("2026-07-25T01:00:00Z");

	@Autowired
	ScheduleDateHorizonService horizonService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 운영_날짜를_초기화한다() {
		given(clock.getZone()).willReturn(SEOUL_ZONE);
		given(clock.instant()).willReturn(FIRST_DAY);
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 5,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
	}

	@Test
	void 오늘부터_3개월_후까지_양끝을_포함해_생성한다() {
		final ScheduleDateHorizonResult result = horizonService.ensureHorizon();
		final LocalDate today = LocalDate.of(2026, 7, 24);
		final LocalDate endDate = today.plusMonths(3);

		assertThat(result.startDate()).isEqualTo(today);
		assertThat(result.endDate()).isEqualTo(endDate);
		assertThat(result.insertedCount())
			.isEqualTo(ChronoUnit.DAYS.between(today, endDate) + 1);
		assertThat(result.appliedConfigVersion()).isEqualTo(5L);
		assertThat(findScheduleDates()).startsWith(today).endsWith(endDate);
		assertThat(countDistinctAppliedVersions()).isOne();
		assertThat(findOnlyAppliedVersion()).isEqualTo(5L);
	}

	@Test
	void 다음날_새_마지막_날짜만_보충하고_과거_날짜를_보존한다() {
		final LocalDate preservedPastDate = LocalDate.of(2020, 1, 1);
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES (?, 'NORMAL', 2)
			""", preservedPastDate);
		horizonService.ensureHorizon();
		given(clock.instant()).willReturn(NEXT_DAY);

		final ScheduleDateHorizonResult nextDayResult = horizonService.ensureHorizon();

		assertThat(nextDayResult.insertedCount()).isOne();
		assertThat(findScheduleDates()).contains(
			preservedPastDate,
			LocalDate.of(2026, 10, 25));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT applied_config_version
			FROM schedule_dates
			WHERE schedule_date = ?
			""", Long.class, preservedPastDate)).isEqualTo(2L);
	}

	@Test
	void 반복_실행은_같은_날짜를_중복_생성하지_않는다() {
		final ScheduleDateHorizonResult first = horizonService.ensureHorizon();
		final ScheduleDateHorizonResult second = horizonService.ensureHorizon();

		assertThat(first.insertedCount()).isPositive();
		assertThat(second.insertedCount()).isZero();
		assertThat(countScheduleDates()).isEqualTo(first.insertedCount());
	}

	@Test
	void SYNCING_중에는_horizon을_보충하지_않는다() {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				active_version = 5,
				pending_version = 6,
				sync_started_at = '2026-07-24 10:00:00',
				sync_started_by = 'schedule-admin'
			WHERE id = 1
			""");

		final ScheduleDateHorizonResult result = horizonService.ensureHorizon();

		assertThat(result.insertedCount()).isZero();
		assertThat(result.appliedConfigVersion()).isEqualTo(5L);
		assertThat(countScheduleDates()).isZero();
	}

	@Test
	void 동시_실행에도_날짜별_행은_하나만_생성한다() throws Exception {
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<ScheduleDateHorizonResult> first = executor.submit(
				() -> runAfterSignal(ready, start));
			final Future<ScheduleDateHorizonResult> second = executor.submit(
				() -> runAfterSignal(ready, start));
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final int insertedCount = first.get(10, TimeUnit.SECONDS).insertedCount()
				+ second.get(10, TimeUnit.SECONDS).insertedCount();
			assertThat(countScheduleDates()).isEqualTo(insertedCount);
			assertThat(countScheduleDates()).isEqualTo(
				ChronoUnit.DAYS.between(
					LocalDate.of(2026, 7, 24),
					LocalDate.of(2026, 10, 24)) + 1);
			assertThat(countDuplicateDates()).isZero();
		}
	}

	private ScheduleDateHorizonResult runAfterSignal(
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
		return horizonService.ensureHorizon();
	}

	private List<LocalDate> findScheduleDates() {
		return jdbcTemplate.queryForList("""
			SELECT schedule_date
			FROM schedule_dates
			ORDER BY schedule_date
			""", LocalDate.class);
	}

	private long countScheduleDates() {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM schedule_dates",
			Long.class);
	}

	private long countDuplicateDates() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM (
				SELECT schedule_date
				FROM schedule_dates
				GROUP BY schedule_date
				HAVING COUNT(*) > 1
			) duplicate_dates
			""", Long.class);
	}

	private long countDistinctAppliedVersions() {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(DISTINCT applied_config_version) FROM schedule_dates",
			Long.class);
	}

	private long findOnlyAppliedVersion() {
		return jdbcTemplate.queryForObject(
			"SELECT MIN(applied_config_version) FROM schedule_dates",
			Long.class);
	}
}
