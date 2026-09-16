package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ScheduleTemplateFutureReservationQueryCountIntegrationTest {

	private static final Instant NOW = Instant.parse("2031-04-07T01:00:00Z");
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,"LARGE_ARENA_BEGINNER":3,
		"LARGE_ARENA_TROT":3,"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";

	@Autowired
	ScheduleTemplateFutureReservationQueryService service;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@Test
	void 예약과_회원이_늘어나도_Template별_미래_조회는_두_번의_bounded_query를_유지한다() {
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
		final long templateId = insertTemplate();
		insertFutureReservation(templateId, 0);
		final Statistics statistics = statistics();

		final QueryMeasurement oneRow = measure(statistics, templateId);
		for (int index = 1; index <= 20; index++) {
			insertFutureReservation(templateId, index);
		}
		final QueryMeasurement manyRows = measure(statistics, templateId);

		assertThat(oneRow.reservationCount()).isOne();
		assertThat(manyRows.reservationCount()).isEqualTo(21);
		assertThat(manyRows.queryCount()).isEqualTo(oneRow.queryCount());
		assertThat(manyRows.queryCount()).isEqualTo(2);
	}

	private QueryMeasurement measure(Statistics statistics, long templateId) {
		entityManager.clear();
		statistics.clear();
		final ScheduleTemplateFutureReservationsResult result = service.find(templateId);
		return new QueryMeasurement(result.reservationCount(), statistics.getPrepareStatementCount());
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private long insertTemplate() {
		jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, active, created_by, updated_by
			) VALUES ('TUESDAY', '09:00:00', '09:45:00', 8, 4, ?, TRUE, 'm34-02-query', 'm34-02-query')
			""", CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertFutureReservation(long templateId, int index) {
		final LocalDate lessonDate = LocalDate.of(2031, 4, 8).plusDays(index);
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, '09:00:00', '09:45:00', 'TEMPLATE', ?, 8, 4, ?)
			""", lessonDate, templateId, CLASS_CAPACITIES);
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-3402-0000', FALSE)
			""", "m34-02-query-" + index, "미래 조회 회원 " + index);
		final long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'ROUND_TROT', ?, '09:00:00', 'confirmed', 'single_payment',
				?, ?, ?)
			""",
			memberId,
			lessonDate,
			lessonDate.atTime(6, 0),
			lessonDate.minusDays(1).atTime(9, 0),
			lessonDate.minusDays(1).atTime(10, 0));
	}

	private record QueryMeasurement(int reservationCount, long queryCount) {
	}
}
