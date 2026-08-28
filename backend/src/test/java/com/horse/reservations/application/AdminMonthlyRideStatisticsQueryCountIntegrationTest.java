package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

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
class AdminMonthlyRideStatisticsQueryCountIntegrationTest {

	@Autowired
	AdminMonthlyRideStatisticsService service;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 회원과_완료_예약이_늘어나도_DB_집계_쿼리_수는_증가하지_않는다() {
		insertMemberWithCompletedRides("monthly-query-one", "한 명", 1);
		final Statistics statistics = statistics();

		final QueryMeasurement oneMember = measure(statistics);
		for (int index = 0; index < 20; index++) {
			insertMemberWithCompletedRides("monthly-query-many-" + index, "여러 명 " + index, 5);
		}
		final QueryMeasurement manyMembers = measure(statistics);

		assertThat(oneMember.result().totalCompletedRideCount()).isEqualTo(1);
		assertThat(manyMembers.result().totalCompletedRideCount()).isEqualTo(101);
		assertThat(manyMembers.result().leaders()).hasSize(20);
		assertThat(manyMembers.queryCount()).isEqualTo(oneMember.queryCount());
		assertThat(manyMembers.queryCount()).isEqualTo(1);
	}

	private QueryMeasurement measure(Statistics statistics) {
		entityManager.clear();
		statistics.clear();
		final AdminMonthlyRideStatisticsResult result = service.getStatistics(YearMonth.of(2026, 9), "ALL");
		return new QueryMeasurement(result, statistics.getPrepareStatementCount());
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private void insertMemberWithCompletedRides(String authSubject, String name, int rideCount) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-2222-3333', FALSE)
			""", authSubject, name);
		final Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		for (int index = 0; index < rideCount; index++) {
			jdbcTemplate.update("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at, admin_confirmed_at
				) VALUES (?, 'ROUND_TROT', '2026-09-15', ?, 'completed', 'single_payment',
					'2026-09-01 12:00:00', '2026-09-01 09:00:00', '2026-09-01 10:00:00')
				""", memberId, "%02d:00:00".formatted(8 + index));
		}
	}

	private record QueryMeasurement(AdminMonthlyRideStatisticsResult result, long queryCount) {
	}
}
