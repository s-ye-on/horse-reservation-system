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
class MemberReservationQueryApiTest {

	private static final String ENDPOINT = "/api/me/reservations";
	private static final String MEMBER_SUBJECT = "reservation-list-member";
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
	void 회원은_자신의_전체_예약을_예정_우선과_과거_최신순으로_조회한다() throws Exception {
		final Long memberId = insertMember(MEMBER_SUBJECT);
		final Long otherMemberId = insertMember("other-reservation-member");
		final Long couponId = insertCoupon(memberId);
		final Long pendingApprovalId = insertCouponReservation(
			memberId, couponId, "2026-07-15", "09:00:00", "pending_admin_approval");
		final Long pendingPaymentId = insertSinglePaymentReservation(
			memberId, "2026-07-16", "10:00:00", "pending_payment");
		insertSinglePaymentReservation(memberId, "2026-07-17", "09:00:00", "payment_expired");
		insertRejectedReservation(memberId, "2026-07-18", "11:00:00");
		insertConfirmedReservation(memberId, "2026-07-19", "09:00:00", "confirmed");
		insertConfirmedReservation(memberId, "2026-07-20", "09:00:00", "completed");
		insertCancelledReservation(memberId, "2026-07-21", "09:00:00");
		insertNoShowReservation(memberId, "2026-07-22", "09:00:00");
		final Long recentPastId = insertConfirmedReservation(
			memberId, "2026-07-14", "11:00:00", "completed");
		final Long oldPastId = insertConfirmedReservation(
			memberId, "2026-07-13", "09:00:00", "completed");
		insertSinglePaymentReservation(otherMemberId, "2026-07-15", "08:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT).with(memberJwt(MEMBER_SUBJECT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(10))
			.andExpect(jsonPath("$[0].reservationId").value(pendingPaymentId))
			.andExpect(jsonPath("$[0].paymentDueAt").value("2026-07-15T12:00:00"))
			.andExpect(jsonPath("$[0].displayGroup").value("UPCOMING"))
			.andExpect(jsonPath("$[0].actions.change.allowed").value(true))
			.andExpect(jsonPath("$[0].actions.cancel.allowed").value(true))
			.andExpect(jsonPath("$[0].actions.approve.allowed").value(true))
			.andExpect(jsonPath("$[0].actions.complete.allowed").value(false))
			.andExpect(jsonPath("$[0].actions.complete.blockedReason")
				.value("RESERVATION_INVALID_STATUS"))
			.andExpect(jsonPath("$[1].status").value("payment_expired"))
			.andExpect(jsonPath("$[2].status").value("rejected"))
			.andExpect(jsonPath("$[2].rejectionReason").value("관리자 승인 반려"))
			.andExpect(jsonPath("$[3].status").value("confirmed"))
			.andExpect(jsonPath("$[4].status").value("completed"))
			.andExpect(jsonPath("$[5].status").value("cancelled"))
			.andExpect(jsonPath("$[6].status").value("no_show"))
			.andExpect(jsonPath("$[6].couponAction").value("none"))
			.andExpect(jsonPath("$[7].reservationId").value(pendingApprovalId))
			.andExpect(jsonPath("$[7].status").value("pending_admin_approval"))
			.andExpect(jsonPath("$[7].displayGroup").value("PAST"))
			.andExpect(jsonPath("$[7].coupon.couponId").value(couponId))
			.andExpect(jsonPath("$[7].coupon.availableCount").value(9))
			.andExpect(jsonPath("$[7].actions.change.allowed").value(false))
			.andExpect(jsonPath("$[7].actions.change.blockedReason")
				.value("RESERVATION_LESSON_ALREADY_STARTED"))
			.andExpect(jsonPath("$[7].actions.approve.allowed").value(false))
			.andExpect(jsonPath("$[7].actions.approve.blockedReason")
				.value("RESERVATION_LESSON_ALREADY_STARTED"))
			.andExpect(jsonPath("$[8].reservationId").value(recentPastId))
			.andExpect(jsonPath("$[9].reservationId").value(oldPastId))
			.andExpect(jsonPath("$[7].adminMemo").doesNotExist())
			.andExpect(jsonPath("$[7].rejectedBy").doesNotExist())
			.andExpect(jsonPath("$[7].cancellationResponsibility").doesNotExist());
	}

	@Test
	void 예약_조회는_예약과_쿠폰_상태를_변경하지_않는다() throws Exception {
		final Long memberId = insertMember(MEMBER_SUBJECT);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, "2026-07-16", "09:00:00", "pending_admin_approval");
		final String reservationBefore = reservationSnapshot(reservationId);
		final String couponBefore = couponSnapshot(couponId);

		mockMvc.perform(get(ENDPOINT).with(memberJwt(MEMBER_SUBJECT)))
			.andExpect(status().isOk());

		assertThat(reservationSnapshot(reservationId)).isEqualTo(reservationBefore);
		assertThat(couponSnapshot(couponId)).isEqualTo(couponBefore);
	}

	@Test
	void 인증_주체와_연결된_회원이_없으면_다른_회원_예약을_조회하지_못한다() throws Exception {
		final Long memberId = insertMember(MEMBER_SUBJECT);
		insertSinglePaymentReservation(memberId, "2026-07-16", "09:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT).with(memberJwt("missing-member")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt("admin-without-member")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isUnauthorized());
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '예약 조회 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'query-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(
		Long memberId,
		Long couponId,
		String lessonDate,
		String startTime,
		String reservationStatus
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'coupon', ?, '2026-07-15 09:00:00')
			""", memberId, lessonDate, startTime, reservationStatus, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(
		Long memberId,
		String lessonDate,
		String startTime,
		String reservationStatus
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00')
			""", memberId, lessonDate, startTime, reservationStatus);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(
		Long memberId,
		String lessonDate,
		String startTime,
		String reservationStatus
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00', '2026-07-15 10:00:00')
			""", memberId, lessonDate, startTime, reservationStatus);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertRejectedReservation(Long memberId, String lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, rejected_at, rejected_by, rejection_reason
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'rejected', 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00', '2026-07-15 10:00:00',
				'reject-admin', '관리자 승인 반려')
			""", memberId, lessonDate, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCancelledReservation(Long memberId, String lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, cancelled_at, cancellation_responsibility
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'cancelled', 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00', '2026-07-15 10:00:00', 'member')
			""", memberId, lessonDate, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertNoShowReservation(Long memberId, String lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at, coupon_action, admin_memo
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'no_show', 'single_payment',
				'2026-07-15 12:00:00', '2026-07-15 09:00:00', '2026-07-15 10:00:00',
				'none', '내부 관리자 메모')
			""", memberId, lessonDate, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String reservationSnapshot(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(status, ':', version, ':', updated_at) FROM reservations WHERE id = ?
			""", String.class, reservationId);
	}

	private String couponSnapshot(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(remaining_count, ':', held_count, ':', status, ':', updated_at)
			FROM coupons WHERE id = ?
			""", String.class, couponId);
	}

	private RequestPostProcessor memberJwt(String subject) {
		return jwt().jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private RequestPostProcessor adminJwt(String subject) {
		return jwt().jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
