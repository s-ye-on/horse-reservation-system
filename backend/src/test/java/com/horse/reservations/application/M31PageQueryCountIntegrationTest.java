package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

import jakarta.persistence.EntityManagerFactory;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class M31PageQueryCountIntegrationTest {

	private static final int EXPECTED_BATCHED_QUERY_COUNT = 4;

	@Autowired
	AdminReservationQueryService adminReservationQueryService;

	@Autowired
	MemberReservationQueryService memberReservationQueryService;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 관리자_예약_페이지는_항목_수와_무관하게_회원과_쿠폰을_일괄_조회한다() {
		insertReservationWithDistinctReferences(
			"admin-query-count-member-a", "2026-08-10", "09:00:00");
		insertReservationWithDistinctReferences(
			"admin-query-count-member-b", "2026-08-11", "10:00:00");
		insertReservationWithDistinctReferences(
			"admin-query-count-member-c", "2026-08-12", "11:00:00");
		final Statistics statistics = statistics();

		statistics.clear();
		adminReservationQueryService.getReservations(
			null,
			null,
			null,
			null,
			null,
			null,
			0,
			3);

		assertThat(statistics.getPrepareStatementCount())
			.isEqualTo(EXPECTED_BATCHED_QUERY_COUNT);

	}

	@Test
	void 회원_예약_페이지는_항목_수와_무관하게_쿠폰을_일괄_조회한다() {
		final String authSubject = "member-query-count-member";
		final Long memberId = insertMember(authSubject);
		insertReservation(memberId, insertCoupon(memberId), "2026-08-10", "09:00:00");
		insertReservation(memberId, insertCoupon(memberId), "2026-08-11", "10:00:00");
		insertReservation(memberId, insertCoupon(memberId), "2026-08-12", "11:00:00");
		final Statistics statistics = statistics();

		statistics.clear();
		memberReservationQueryService.getMyReservations(authSubject, 0, 3);

		assertThat(statistics.getPrepareStatementCount())
			.isEqualTo(EXPECTED_BATCHED_QUERY_COUNT);

		statistics.clear();
		memberReservationQueryService.getMyReservations(
			authSubject,
			"UPCOMING",
			"pending_admin_approval",
			0,
			3);

		assertThat(statistics.getPrepareStatementCount())
			.isEqualTo(EXPECTED_BATCHED_QUERY_COUNT);

		statistics.clear();
		memberReservationQueryService.getMyReservations(
			authSubject,
			null,
			"pending_admin_approval",
			0,
			3);

		assertThat(statistics.getPrepareStatementCount())
			.isEqualTo(EXPECTED_BATCHED_QUERY_COUNT);
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '쿼리 수 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 3, 'active', 'query-count-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertReservationWithDistinctReferences(
		String authSubject,
		String lessonDate,
		String startTime
	) {
		final Long memberId = insertMember(authSubject);
		insertReservation(memberId, insertCoupon(memberId), lessonDate, startTime);
	}

	private void insertReservation(
		Long memberId,
		Long couponId,
		String lessonDate,
		String startTime
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'pending_admin_approval', 'coupon', ?,
				'2026-07-29 09:00:00')
			""", memberId, lessonDate, startTime, couponId);
	}
}
