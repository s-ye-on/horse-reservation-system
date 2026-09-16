package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class ScheduleTemplateFutureReservationQueryPlanIntegrationTest {

	private static final int ROW_COUNT = 1_200;
	private static final LocalDate TODAY = LocalDate.of(2031, 4, 7);
	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,"LARGE_ARENA_BEGINNER":3,
		"LARGE_ARENA_TROT":3,"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 미래_점유_예약_조회는_Template과_날짜로_TimeSlot_범위를_먼저_제한한다() {
		final long targetTemplateId = insertTemplate("MONDAY", "09:00:00");
		final long otherTemplateId = insertTemplate("TUESDAY", "09:00:00");
		final long memberId = insertMember();
		insertTimeSlots(targetTemplateId, otherTemplateId);
		insertReservations(memberId);

		final List<Map<String, Object>> plan = jdbcTemplate.queryForList("""
			EXPLAIN
			SELECT reservation.id, reservation.lesson_date, reservation.start_time,
				reservation.end_time, member_row.id, member_row.name, member_row.phone,
				reservation.class_type, reservation.status
			FROM time_slot_capacities time_slot
			STRAIGHT_JOIN reservations reservation
			  ON reservation.lesson_date = time_slot.lesson_date
			 AND reservation.start_time = time_slot.start_time
			STRAIGHT_JOIN members member_row ON member_row.id = reservation.member_id
			WHERE time_slot.template_id = ?
			  AND reservation.status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
			  AND (
				time_slot.lesson_date > ?
				OR (time_slot.lesson_date = ? AND time_slot.start_time > '10:00:00')
			  )
			ORDER BY reservation.lesson_date, reservation.start_time, reservation.id
			""", targetTemplateId, TODAY, TODAY);

		final Map<String, Object> reservationPlan = rowFor(plan, "reservation");
		final Map<String, Object> timeSlotPlan = rowFor(plan, "time_slot");
		assertThat(String.valueOf(timeSlotPlan.get("key")))
			.as("time slot plan: %s", timeSlotPlan)
			.isEqualTo("idx_time_slot_template_lesson_start");
		assertThat(String.valueOf(timeSlotPlan.get("type")))
			.as("time slot plan: %s", timeSlotPlan)
			.isIn("range", "ref");
		assertThat(String.valueOf(reservationPlan.get("key")))
			.as("reservation plan: %s", reservationPlan)
			.isIn("idx_reservations_occupancy", "uk_reservations_active_member_slot");
		assertThat(String.valueOf(reservationPlan.get("type")))
			.as("reservation plan: %s", reservationPlan)
			.isIn("ref", "range");
	}

	private Map<String, Object> rowFor(List<Map<String, Object>> plan, String table) {
		return plan.stream()
			.filter(row -> table.equals(String.valueOf(row.get("table"))))
			.findFirst()
			.orElseThrow();
	}

	private long insertTemplate(String dayOfWeek, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, active, created_by, updated_by
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 8, 4, ?, TRUE, 'm34-02-plan', 'm34-02-plan')
			""", dayOfWeek, startTime, startTime, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertMember() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES ('m34-02-plan-member', '실행 계획 회원', '010-3402-0000', FALSE)
			""");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertTimeSlots(long targetTemplateId, long otherTemplateId) {
		jdbcTemplate.batchUpdate("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'TEMPLATE', ?, 8, 4, ?)
			""", new BatchPreparedStatementSetter() {

				@Override
				public void setValues(PreparedStatement statement, int index) throws SQLException {
					final LocalDate lessonDate = TODAY.minusDays(150).plusDays(index / 4);
					final LocalTime startTime = LocalTime.of(8 + index % 4, 0);
					statement.setObject(1, lessonDate);
					statement.setObject(2, startTime);
					statement.setObject(3, startTime);
					statement.setLong(4, index % 10 == 0 ? targetTemplateId : otherTemplateId);
					statement.setString(5, CLASS_CAPACITIES);
				}

				@Override
				public int getBatchSize() {
					return ROW_COUNT;
				}
			});
	}

	private void insertReservations(long memberId) {
		jdbcTemplate.batchUpdate("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'ROUND_TROT', ?, ?, ?, 'single_payment', ?, ?, ?)
			""", new BatchPreparedStatementSetter() {

				@Override
				public void setValues(PreparedStatement statement, int index) throws SQLException {
					final LocalDate lessonDate = TODAY.minusDays(150).plusDays(index / 4);
					final LocalTime startTime = LocalTime.of(8 + index % 4, 0);
					final boolean confirmed = index % 2 == 0;
					statement.setLong(1, memberId);
					statement.setObject(2, lessonDate);
					statement.setObject(3, startTime);
					statement.setString(4, confirmed ? "confirmed" : "completed");
					statement.setObject(5, lessonDate.atTime(6, 0));
					statement.setObject(6, lessonDate.minusDays(1).atTime(9, 0));
					statement.setObject(7, lessonDate.minusDays(1).atTime(10, 0));
				}

				@Override
				public int getBatchSize() {
					return ROW_COUNT;
				}
			});
	}
}
