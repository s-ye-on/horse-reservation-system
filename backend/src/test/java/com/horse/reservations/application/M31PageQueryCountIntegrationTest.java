package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;

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
class M31PageQueryCountIntegrationTest {

	@Autowired
	AdminReservationQueryService adminReservationQueryService;

	@Autowired
	MemberReservationQueryService memberReservationQueryService;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	Clock clock;

	@Test
	void 관리자_예약_페이지는_항목_수와_무관하게_회원과_쿠폰을_일괄_조회한다() {
		final LocalDate firstLessonDate = LocalDate.now(clock).plusDays(1);
		insertReservationWithDistinctReferences(
			"admin-query-count-member-a", firstLessonDate, "09:00:00");
		insertReservationWithDistinctReferences(
			"admin-query-count-member-b", firstLessonDate.plusDays(1), "10:00:00");
		insertReservationWithDistinctReferences(
			"admin-query-count-member-c", firstLessonDate.plusDays(2), "11:00:00");
		insertReservationWithDistinctReferences(
			"admin-query-count-member-d", firstLessonDate.plusDays(3), "13:30:00");
		final Statistics statistics = statistics();

		final QueryMeasurement<AdminReservationPageResult> singleItem = measureQueries(() ->
			adminReservationQueryService.getReservations(
			null,
			firstLessonDate,
			firstLessonDate.plusDays(3),
			null,
			null,
			null,
			0,
			1), statistics);
		final QueryMeasurement<AdminReservationPageResult> threeItems = measureQueries(() ->
			adminReservationQueryService.getReservations(
			null,
			firstLessonDate,
			firstLessonDate.plusDays(3),
			null,
			null,
			null,
			0,
			3), statistics);

		assertThat(singleItem.result().content()).hasSize(1);
		assertThat(threeItems.result().content())
			.hasSize(3)
			.allSatisfy(reservation -> {
				assertThat(reservation.memberName()).isEqualTo("쿼리 수 회원");
				assertThat(reservation.coupon()).isNotNull();
			});
		assertThat(threeItems.result().content())
			.extracting(reservation -> reservation.coupon().couponId())
			.doesNotHaveDuplicates();
		assertThat(threeItems.result().totalElements()).isEqualTo(4);
		assertThat(threeItems.queryCount()).isEqualTo(singleItem.queryCount());
	}

	@Test
	void 회원_예약_페이지는_항목_수와_무관하게_쿠폰을_일괄_조회한다() {
		final String authSubject = "member-query-count-member";
		final Long memberId = insertMember(authSubject);
		final LocalDate firstLessonDate = LocalDate.now(clock).plusDays(1);
		insertReservation(memberId, insertCoupon(memberId), firstLessonDate, "09:00:00");
		insertReservation(memberId, insertCoupon(memberId), firstLessonDate.plusDays(1), "10:00:00");
		insertReservation(memberId, insertCoupon(memberId), firstLessonDate.plusDays(2), "11:00:00");
		insertReservation(memberId, insertCoupon(memberId), firstLessonDate.plusDays(3), "13:30:00");
		final Statistics statistics = statistics();

		assertMemberPageQueryCountDoesNotGrow(authSubject, null, null, statistics);
		assertMemberPageQueryCountDoesNotGrow(
			authSubject, "UPCOMING", "pending_admin_approval", statistics);
		assertMemberPageQueryCountDoesNotGrow(
			authSubject, null, "pending_admin_approval", statistics);
	}

	private void assertMemberPageQueryCountDoesNotGrow(
		String authSubject,
		String displayGroup,
		String status,
		Statistics statistics
	) {
		final QueryMeasurement<MemberReservationPageResult> singleItem = measureQueries(() ->
			memberReservationQueryService.getMyReservations(
				authSubject, displayGroup, status, 0, 1), statistics);
		final QueryMeasurement<MemberReservationPageResult> threeItems = measureQueries(() ->
			memberReservationQueryService.getMyReservations(
				authSubject, displayGroup, status, 0, 3), statistics);

		assertThat(singleItem.result().content()).hasSize(1);
		assertThat(threeItems.result().content())
			.hasSize(3)
			.allSatisfy(reservation -> assertThat(reservation.coupon()).isNotNull());
		assertThat(threeItems.result().content())
			.extracting(reservation -> reservation.coupon().couponId())
			.doesNotHaveDuplicates();
		assertThat(threeItems.result().totalElements()).isEqualTo(4);
		assertThat(threeItems.queryCount()).isEqualTo(singleItem.queryCount());
	}

	private <T> QueryMeasurement<T> measureQueries(
		java.util.function.Supplier<T> query,
		Statistics statistics
	) {
		entityManager.clear();
		statistics.clear();
		final T result = query.get();
		return new QueryMeasurement<>(result, statistics.getPrepareStatementCount());
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
		LocalDate lessonDate,
		String startTime
	) {
		final Long memberId = insertMember(authSubject);
		insertReservation(memberId, insertCoupon(memberId), lessonDate, startTime);
	}

	private void insertReservation(
		Long memberId,
		Long couponId,
		LocalDate lessonDate,
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

	private record QueryMeasurement<T>(T result, long queryCount) {
	}
}
