package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
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
import com.horse.global.exception.BusinessException;
import com.horse.timeslots.application.AdminTimeSlotService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminTimeSlotScheduleDateLockIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 20);
	private static final Map<String, Integer> CLASS_CAPACITIES = Map.of(
		"FIRST_RIDE", 8,
		"ROUND_BEGINNER", 4,
		"ROUND_TROT", 4,
		"LARGE_ARENA_BEGINNER", 8,
		"LARGE_ARENA_TROT", 8,
		"DRESSAGE", 8,
		"JUMPING", 8);
	private static final String CLASS_CAPACITIES_JSON = """
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
	JdbcTemplate jdbcTemplate;

	@Autowired
	AdminTimeSlotService adminTimeSlotService;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 날짜와_시간대를_준비한다() {
		clearFixture();
		when(clock.instant()).thenReturn(Instant.parse("2026-08-01T01:00:00Z"));
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 1,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (
				schedule_date, status, applied_config_version
			) VALUES (?, 'NORMAL', 1)
			""", LESSON_DATE);
	}

	@AfterEach
	void 생성한_날짜와_시간대를_정리한다() {
		clearFixture();
	}

	@Test
	void 정원_변경은_ScheduleDate_잠금을_기다린_뒤_CLOSING을_재검증한다() {
		final Long timeSlotId = insertTimeSlot("09:00:00");

		final String resultCode = executeWhileClosingTransitionHoldsLock(() ->
			adminTimeSlotService.changeCapacity(timeSlotId, 7, 4, CLASS_CAPACITIES));

		assertThat(resultCode).isEqualTo("SCHEDULE_DATE_NOT_RESERVABLE");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT total_capacity
			FROM time_slot_capacities
			WHERE id = ?
			""", Integer.class, timeSlotId)).isEqualTo(8);
	}

	@Test
	void 삭제는_ScheduleDate_잠금을_기다린_뒤_CLOSING을_재검증한다() {
		final Long timeSlotId = insertTimeSlot("10:00:00");

		final String resultCode = executeWhileClosingTransitionHoldsLock(() ->
			adminTimeSlotService.deleteTimeSlot(timeSlotId));

		assertThat(resultCode).isEqualTo("SCHEDULE_DATE_NOT_RESERVABLE");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM time_slot_capacities
			WHERE id = ?
			""", Integer.class, timeSlotId)).isOne();
	}

	private String executeWhileClosingTransitionHoldsLock(Runnable command) {
		final CountDownLatch dateLocked = new CountDownLatch(1);
		final CountDownLatch releaseDateLock = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final Future<?> closingTransition = executor.submit(() ->
				startClosingAndHoldLock(dateLocked, releaseDateLock));
			await(dateLocked);
			final Future<String> commandResult = executor.submit(() -> executeCommand(command));

			assertThatThrownBy(() -> commandResult.get(250, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseDateLock.countDown();
			getResult(closingTransition);
			return getResult(commandResult);
		}
		finally {
			releaseDateLock.countDown();
			executor.shutdownNow();
		}
	}

	private void startClosingAndHoldLock(
		CountDownLatch dateLocked,
		CountDownLatch releaseDateLock
	) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbcTemplate.queryForObject("""
				SELECT id
				FROM schedule_dates
				WHERE schedule_date = ?
				FOR UPDATE
				""", Long.class, LESSON_DATE);
			jdbcTemplate.update("""
				UPDATE schedule_dates
				SET status = 'CLOSING',
					resume_status = 'NORMAL',
					reason = 'M31-07 잠금 검증',
					changed_by = 'm31-07-test'
				WHERE schedule_date = ?
				""", LESSON_DATE);
			dateLocked.countDown();
			await(releaseDateLock);
		});
	}

	private String executeCommand(Runnable command) {
		try {
			command.run();
			return "SUCCEEDED";
		}
		catch (BusinessException exception) {
			return exception.code();
		}
	}

	private Long insertTimeSlot(String startTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source,
				total_capacity, round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'MANUAL', 8, 4, ?)
			""", LESSON_DATE, startTime, startTime, CLASS_CAPACITIES_JSON);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void clearFixture() {
		jdbcTemplate.update("DELETE FROM time_slot_capacities WHERE lesson_date = ?", LESSON_DATE);
		jdbcTemplate.update("DELETE FROM schedule_dates WHERE schedule_date = ?", LESSON_DATE);
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private <T> T getResult(Future<T> future) {
		try {
			return future.get(5, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}
}
