package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class AdminWeeklyOperationsCalendarQueryCountIntegrationTest {

	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,"LARGE_ARENA_BEGINNER":3,
		"LARGE_ARENA_TROT":3,"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";
	private static final LocalDate WEEK_START = LocalDate.of(2031, 4, 7);

	@Autowired
	AdminWeeklyOperationsCalendarService service;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 슬롯_예약_회원이_늘어나도_주간_조회는_두_번의_bounded_query를_유지한다() {
		insertCalendarRow(0);
		final Statistics statistics = statistics();

		final QueryMeasurement oneRow = measure(statistics);
		for (int index = 1; index <= 20; index++) {
			insertCalendarRow(index);
		}
		final QueryMeasurement manyRows = measure(statistics);

		assertThat(oneRow.timeSlotCount()).isOne();
		assertThat(oneRow.reservationCount()).isOne();
		assertThat(manyRows.timeSlotCount()).isEqualTo(21);
		assertThat(manyRows.reservationCount()).isEqualTo(21);
		assertThat(manyRows.queryCount()).isEqualTo(oneRow.queryCount());
		assertThat(manyRows.queryCount()).isEqualTo(2);
	}

	private QueryMeasurement measure(Statistics statistics) {
		entityManager.clear();
		statistics.clear();
		final AdminWeeklyOperationsCalendarResult result = service.getCalendar(WEEK_START.plusDays(3));
		final int reservationCount = result.timeSlots().stream()
			.mapToInt(timeSlot -> timeSlot.reservations().size())
			.sum();
		return new QueryMeasurement(
			result.timeSlots().size(),
			reservationCount,
			statistics.getPrepareStatementCount());
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private void insertCalendarRow(int index) {
		final LocalDate lessonDate = WEEK_START.plusDays(index % 7);
		final LocalTime startTime = LocalTime.of(8 + index / 7, index % 7 * 5);
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'MANUAL', 8, 4, ?)
			""", lessonDate, startTime, startTime, CLASS_CAPACITIES);
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-8000-0000', FALSE)
			""", "weekly-query-" + index, "주간 조회 회원 " + index);
		final Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'ROUND_TROT', ?, ?, 'confirmed', 'single_payment', ?, ?, ?)
			""",
			memberId,
			lessonDate,
			startTime,
			lessonDate.atTime(6, 0),
			lessonDate.minusDays(1).atTime(9, 0),
			lessonDate.minusDays(1).atTime(10, 0));
	}

	private record QueryMeasurement(int timeSlotCount, int reservationCount, long queryCount) {
	}

}
