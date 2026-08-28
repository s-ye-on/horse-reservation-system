package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
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
class AdminMonthlyRideStatisticsQueryPlanIntegrationTest {

	private static final int RESERVATION_COUNT = 1_200;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 월간_집계는_기존_일자_상태_인덱스를_후보로_사용하고_full_scan을_피한다() {
		final List<Long> memberIds = insertMembers();
		insertCompletedReservations(memberIds);

		final Map<String, Object> plan = jdbcTemplate.queryForList("""
			EXPLAIN
			SELECT member.id, member.name, COUNT(reservation.id)
			FROM reservations reservation
			JOIN members member ON member.id = reservation.member_id
			WHERE reservation.status = 'completed'
			  AND reservation.lesson_date >= '2026-09-01'
			  AND reservation.lesson_date < '2026-10-01'
			  AND reservation.class_type IN (
				'FIRST_RIDE', 'ROUND_BEGINNER', 'ROUND_TROT', 'LARGE_ARENA_BEGINNER',
				'LARGE_ARENA_TROT', 'CANTER_BEGINNER', 'CANTER', 'DRESSAGE', 'JUMPING'
			  )
			GROUP BY member.id, member.name
			ORDER BY COUNT(reservation.id) DESC, member.id ASC
			""").stream()
			.filter(row -> "reservation".equals(String.valueOf(row.get("table"))))
			.findFirst()
			.orElseThrow();

		assertThat(String.valueOf(plan.get("possible_keys")))
			.contains("idx_reservations_lesson_status");
		assertThat(String.valueOf(plan.get("key")))
			.isIn(
				"idx_reservations_lesson_status",
				"idx_reservations_status_lesson_start",
				"idx_reservations_occupancy");
		assertThat(String.valueOf(plan.get("type"))).isIn("range", "ref");
	}

	private List<Long> insertMembers() {
		final List<Long> memberIds = new ArrayList<>();
		for (int index = 0; index < 200; index++) {
			jdbcTemplate.update("""
				INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
				VALUES (?, ?, '010-3333-4444', FALSE)
				""", "monthly-plan-member-" + index, "실행 계획 회원 " + index);
			memberIds.add(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
		}
		return memberIds;
	}

	private void insertCompletedReservations(List<Long> memberIds) {
		jdbcTemplate.batchUpdate("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'ROUND_TROT', ?, '09:00:00', 'completed', 'single_payment',
				'2026-01-01 12:00:00', '2026-01-01 09:00:00', '2026-01-01 10:00:00')
			""", new BatchPreparedStatementSetter() {

				@Override
				public void setValues(PreparedStatement statement, int index) throws SQLException {
					statement.setLong(1, memberIds.get(index % memberIds.size()));
					statement.setObject(2, LocalDate.of(2025, 1, 1).plusDays(index % 730));
				}

				@Override
				public int getBatchSize() {
					return RESERVATION_COUNT;
				}
			});
	}
}
