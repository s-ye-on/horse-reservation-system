package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminReservationQueryApiTest {

	private static final String ENDPOINT = "/api/admin/reservations";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant QUERY_INSTANT = Instant.parse("2026-07-15T01:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_조회_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(QUERY_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 기본_목록은_오늘_이후를_수업일_시각_예약순으로_조회한다() throws Exception {
		final Long memberId = insertMember("default-query-member", "기본 회원", "010-1111-2222");
		insertSinglePaymentReservation(memberId, "2026-07-14", "09:00:00", "pending_payment");
		final Long secondId = insertSinglePaymentReservation(
			memberId, "2026-07-15", "11:00:00", "pending_payment");
		final Long firstId = insertSinglePaymentReservation(
			memberId, "2026-07-15", "09:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].reservationId").value(firstId))
			.andExpect(jsonPath("$.content[1].reservationId").value(secondId));
	}

	@Test
	void 날짜를_지정하면_과거를_포함하고_상태_클래스_회원_검색을_적용한다() throws Exception {
		final Long matchingMemberId = insertMember("filter-matching", "김하늘", "010-3333-7788");
		final Long otherMemberId = insertMember("filter-other", "박바다", "010-9999-0000");
		final Long matchingId = insertCouponReservation(
			matchingMemberId,
			insertCoupon(matchingMemberId),
			"ROUND_BEGINNER",
			"2026-07-10",
			"09:00:00",
			"2026-07-15 09:00:00");
		insertCouponReservation(
			otherMemberId,
			insertCoupon(otherMemberId),
			"ROUND_BEGINNER",
			"2026-07-10",
			"10:00:00",
			"2026-07-15 09:00:00");
		insertSinglePaymentReservation(otherMemberId, "2026-07-10", "11:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("status", "pending_admin_approval")
				.param("lessonDateFrom", "2026-07-01")
				.param("lessonDateTo", "2026-07-12")
				.param("classType", "ROUND_BEGINNER")
				.param("keyword", "7788"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].reservationId").value(matchingId))
			.andExpect(jsonPath("$.content[0].memberName").value("김하늘"))
			.andExpect(jsonPath("$.content[0].memberPhone").value("010-3333-7788"));

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("lessonDateTo", "2026-07-12")
				.param("keyword", "김하"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].reservationId").value(matchingId));
	}

	@Test
	void 페이지_크기와_고정_정렬을_적용한다() throws Exception {
		final Long memberId = insertMember("page-query-member", "페이지 회원", "010-1234-5678");
		insertSinglePaymentReservation(memberId, "2026-07-16", "09:00:00", "pending_payment");
		final Long secondId = insertSinglePaymentReservation(
			memberId, "2026-07-16", "10:00:00", "pending_payment");
		insertSinglePaymentReservation(memberId, "2026-07-17", "09:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("page", "1")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.content[0].reservationId").value(secondId));
	}

	@Test
	void 상세는_회원_전화번호와_쿠폰과_주요_상태_시각을_제공한다() throws Exception {
		final Long memberId = insertMember("detail-query-member", "상세 회원", "010-2222-4444");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(
			memberId,
			couponId,
			"FIRST_RIDE",
			"2026-07-17",
			"09:00:00",
			"2026-07-15 09:00:00");

		mockMvc.perform(get(ENDPOINT + "/{reservationId}", reservationId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("상세 회원"))
			.andExpect(jsonPath("$.memberPhone").value("010-2222-4444"))
			.andExpect(jsonPath("$.classType").value("FIRST_RIDE"))
			.andExpect(jsonPath("$.status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.coupon.couponId").value(couponId))
			.andExpect(jsonPath("$.coupon.couponType").value("general"))
			.andExpect(jsonPath("$.coupon.remainingCount").value(10))
			.andExpect(jsonPath("$.approvalRequestedAt").value("2026-07-15T09:00:00"))
			.andExpect(jsonPath("$.approvalWarning").value("normal"))
			.andExpect(jsonPath("$.displayGroup").value("UPCOMING"))
			.andExpect(jsonPath("$.actions.change.allowed").value(true))
			.andExpect(jsonPath("$.actions.cancel.allowed").value(true))
			.andExpect(jsonPath("$.actions.approve.allowed").value(true))
			.andExpect(jsonPath("$.actions.complete.allowed").value(false))
			.andExpect(jsonPath("$.actions.complete.blockedReason")
				.value("RESERVATION_INVALID_STATUS"));
	}

	@Test
	void 수업_시작_시각의_확정_예약은_완료와_노쇼만_허용한다() throws Exception {
		final Long memberId = insertMember(
			"started-query-member", "시작 회원", "010-1212-3434");
		final Long reservationId = insertConfirmedReservation(
			memberId, "2026-07-15", "10:00:00");

		mockMvc.perform(get(ENDPOINT + "/{reservationId}", reservationId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayGroup").value("PAST"))
			.andExpect(jsonPath("$.actions.change.allowed").value(false))
			.andExpect(jsonPath("$.actions.change.blockedReason")
				.value("RESERVATION_LESSON_ALREADY_STARTED"))
			.andExpect(jsonPath("$.actions.cancel.allowed").value(false))
			.andExpect(jsonPath("$.actions.complete.allowed").value(true))
			.andExpect(jsonPath("$.actions.noShow.allowed").value(true))
			.andExpect(jsonPath("$.actions.approve.allowed").value(false))
			.andExpect(jsonPath("$.actions.approve.blockedReason")
				.value("RESERVATION_INVALID_STATUS"));
	}

	@Test
	void 승인대기_경고는_신청_경과와_수업_임박을_조회_시점에_계산한다() throws Exception {
		final Long memberId = insertMember("warning-query-member", "경고 회원", "010-5555-6666");
		final Long couponId = insertCoupon(memberId);
		final Long warningId = insertCouponReservation(
			memberId,
			couponId,
			"FIRST_RIDE",
			"2026-07-17",
			"09:00:00",
			"2026-07-15 07:59:59");
		final Long criticalByAgeId = insertCouponReservation(
			memberId,
			couponId,
			"FIRST_RIDE",
			"2026-07-18",
			"09:00:00",
			"2026-07-14 09:59:59");
		final Long criticalByLessonId = insertCouponReservation(
			memberId,
			couponId,
			"FIRST_RIDE",
			"2026-07-16",
			"10:00:00",
			"2026-07-15 09:30:00");

		mockMvc.perform(get(ENDPOINT + "/{reservationId}", warningId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.approvalWarning").value("warning"));
		mockMvc.perform(get(ENDPOINT + "/{reservationId}", criticalByAgeId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.approvalWarning").value("critical"));
		mockMvc.perform(get(ENDPOINT + "/{reservationId}", criticalByLessonId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.approvalWarning").value("critical"));
	}

	@Test
	void 잘못된_필터와_페이지와_없는_예약을_구분해_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("status", "unknown"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_QUERY_STATUS"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("classType", "unknown"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_QUERY_CLASS_TYPE"));
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("lessonDateFrom", "2026-07-20")
				.param("lessonDateTo", "2026-07-19"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_QUERY_DATE_RANGE"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("size", "101"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(ENDPOINT + "/999999").with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void 관리자만_조회할_수_있고_조회는_어떤_데이터도_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("readonly-query-member", "불변 회원", "010-7777-8888");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(
			memberId,
			couponId,
			"FIRST_RIDE",
			"2026-07-17",
			"09:00:00",
			"2026-07-15 09:00:00");
		final String beforeState = databaseState(reservationId, couponId);

		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT).with(adminJwt())).andExpect(status().isOk());
		mockMvc.perform(get(ENDPOINT + "/{reservationId}", reservationId).with(adminJwt()))
			.andExpect(status().isOk());

		assertThat(databaseState(reservationId, couponId)).isEqualTo(beforeState);
	}

	private Long insertMember(String authSubject, String name, String phone) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, ?, FALSE)
			""", authSubject, name, phone);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, 'general', 10, 10, 1, 'query-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(
		Long memberId,
		Long couponId,
		String classType,
		String lessonDate,
		String startTime,
		String approvalRequestedAt
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, ?, ?, ?, 'pending_admin_approval', 'coupon', ?, ?)
			""", memberId, classType, lessonDate, startTime, couponId, approvalRequestedAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(
		Long memberId,
		String lessonDate,
		String startTime,
		String status
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00')
			""", memberId, lessonDate, startTime, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(Long memberId, String lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'confirmed', 'single_payment',
				'2026-07-15 09:00:00', '2026-07-15 08:00:00', '2026-07-15 09:00:00')
			""", memberId, lessonDate, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private String databaseState(Long reservationId, Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(
				reservation.status, ':', reservation.version, ':', coupon.remaining_count, ':',
				coupon.held_count, ':',
				(SELECT COUNT(*) FROM coupon_usage_logs), ':',
				(SELECT COUNT(*) FROM reservation_change_logs)
			)
			FROM reservations reservation
			JOIN coupons coupon ON coupon.id = ?
			WHERE reservation.id = ?
			""", String.class, couponId, reservationId);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
