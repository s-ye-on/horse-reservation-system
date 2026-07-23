package com.horse.schedules.infrastructure;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.horse.schedules.application.RecurringHolidayImpactCounts;

@Repository
public class RecurringHolidayImpactRepository {

	private static final String DATE_PREDICATE = """
		(
			DAYOFWEEK(%1$s) = ?
			AND %1$s BETWEEN ? AND ?
		)
		OR (
			DAYOFWEEK(%1$s) = ?
			AND %1$s BETWEEN ? AND ?
		)
		""";
	private static final String COUNT_AFFECTED_DATES_SQL = """
		SELECT COUNT(DISTINCT schedule_date)
		FROM schedule_dates
		WHERE schedule_date BETWEEN ? AND ?
		  AND (
			%s
		  )
		""".formatted(DATE_PREDICATE.formatted("schedule_date"));
	private static final String COUNT_TEMPLATE_TIME_SLOTS_SQL = """
		SELECT COUNT(DISTINCT id)
		FROM time_slot_capacities
		WHERE source = 'TEMPLATE'
		  AND lesson_date BETWEEN ? AND ?
		  AND (
			%s
		  )
		""".formatted(DATE_PREDICATE.formatted("lesson_date"));
	private static final String COUNT_ACTIVE_RESERVATIONS_SQL = """
		SELECT COUNT(DISTINCT reservation.id)
		FROM reservations reservation
		JOIN time_slot_capacities time_slot
		  ON time_slot.lesson_date = reservation.lesson_date
		 AND time_slot.start_time = reservation.start_time
		WHERE time_slot.source = 'TEMPLATE'
		  AND time_slot.lesson_date BETWEEN ? AND ?
		  AND (
			%s
		  )
		  AND reservation.active_slot_guard = 1
		""".formatted(DATE_PREDICATE.formatted("time_slot.lesson_date"));

	private final JdbcTemplate jdbcTemplate;

	public RecurringHolidayImpactRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public RecurringHolidayImpactCounts count(
		HolidayPeriod period,
		LocalDate horizonStart,
		LocalDate horizonEnd
	) {
		return countCombined(period, period, horizonStart, horizonEnd);
	}

	public RecurringHolidayImpactCounts countCombined(
		HolidayPeriod previous,
		HolidayPeriod current,
		LocalDate horizonStart,
		LocalDate horizonEnd
	) {
		return new RecurringHolidayImpactCounts(
			count(COUNT_AFFECTED_DATES_SQL, previous, current, horizonStart, horizonEnd),
			count(COUNT_TEMPLATE_TIME_SLOTS_SQL, previous, current, horizonStart, horizonEnd),
			count(COUNT_ACTIVE_RESERVATIONS_SQL, previous, current, horizonStart, horizonEnd));
	}

	private long count(
		String sql,
		HolidayPeriod previous,
		HolidayPeriod current,
		LocalDate horizonStart,
		LocalDate horizonEnd
	) {
		return jdbcTemplate.queryForObject(
			sql,
			Long.class,
			horizonStart,
			horizonEnd,
			mysqlDayOfWeek(previous.dayOfWeek()),
			previous.effectiveFrom(),
			previous.effectiveTo(),
			mysqlDayOfWeek(current.dayOfWeek()),
			current.effectiveFrom(),
			current.effectiveTo());
	}

	private int mysqlDayOfWeek(DayOfWeek dayOfWeek) {
		return dayOfWeek == DayOfWeek.SUNDAY ? 1 : dayOfWeek.getValue() + 1;
	}

	public record HolidayPeriod(
		DayOfWeek dayOfWeek,
		LocalDate effectiveFrom,
		LocalDate effectiveTo
	) {
	}
}
