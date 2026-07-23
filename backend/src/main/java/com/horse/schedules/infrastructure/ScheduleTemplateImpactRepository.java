package com.horse.schedules.infrastructure;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.horse.schedules.application.ScheduleTemplateImpactPreview;

@Repository
public class ScheduleTemplateImpactRepository {

	private static final String COUNT_AFFECTED_DATES_SQL = """
		SELECT COUNT(*)
		FROM schedule_dates
		WHERE schedule_date BETWEEN ? AND ?
		  AND DAYOFWEEK(schedule_date) = ?
		""";
	private static final String COUNT_TIME_SLOTS_SQL = """
		SELECT COUNT(*)
		FROM time_slot_capacities
		WHERE (
			lesson_date BETWEEN ? AND ?
			AND DAYOFWEEK(lesson_date) = ?
			AND start_time = ?
		)
		OR (? IS NOT NULL AND template_id = ?)
		""";
	private static final String COUNT_ACTIVE_RESERVATIONS_SQL = """
		SELECT COUNT(*)
		FROM reservations reservation
		JOIN time_slot_capacities time_slot
		  ON time_slot.lesson_date = reservation.lesson_date
		 AND time_slot.start_time = reservation.start_time
		WHERE (
			(
				time_slot.lesson_date BETWEEN ? AND ?
				AND DAYOFWEEK(time_slot.lesson_date) = ?
				AND time_slot.start_time = ?
			)
			OR (? IS NOT NULL AND time_slot.template_id = ?)
		)
		AND reservation.active_slot_guard = 1
		""";

	private final JdbcTemplate jdbcTemplate;

	public ScheduleTemplateImpactRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ScheduleTemplateImpactPreview preview(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		LocalDate startDate,
		LocalDate endDate,
		Long templateId
	) {
		final long affectedDateCount = jdbcTemplate.queryForObject(
			COUNT_AFFECTED_DATES_SQL,
			Long.class,
			startDate,
			endDate,
			mysqlDayOfWeek(dayOfWeek));
		final long timeSlotCount = jdbcTemplate.queryForObject(
			COUNT_TIME_SLOTS_SQL,
			Long.class,
			startDate,
			endDate,
			mysqlDayOfWeek(dayOfWeek),
			startTime,
			templateId,
			templateId);
		final long activeReservationCount = jdbcTemplate.queryForObject(
			COUNT_ACTIVE_RESERVATIONS_SQL,
			Long.class,
			startDate,
			endDate,
			mysqlDayOfWeek(dayOfWeek),
			startTime,
			templateId,
			templateId);
		return new ScheduleTemplateImpactPreview(
			affectedDateCount,
			timeSlotCount,
			activeReservationCount);
	}

	private int mysqlDayOfWeek(DayOfWeek dayOfWeek) {
		return dayOfWeek == DayOfWeek.SUNDAY ? 1 : dayOfWeek.getValue() + 1;
	}
}
