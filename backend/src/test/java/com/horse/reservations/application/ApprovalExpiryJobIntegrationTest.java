package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.horse.reservations.presentation.ApprovalExpiryScheduler;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalExpiryJobIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant EXECUTED_INSTANT = Instant.parse("2026-08-01T00:00:00Z");
	private static final String ENDPOINT = "/api/admin/jobs/expire-pending-approvals";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ApprovalExpiryScheduler scheduler;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_승인_만료_시각을_초기화한다() {
		clearDatabase();
		insertScheduleDate();
		when(clock.instant()).thenReturn(EXECUTED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_수업_시작_시각까지_승인되지_않은_예약과_쿠폰_점유를_만료한다() throws Exception {
		final Long memberId = insertMember("manual-approval-expiry");
		final Long pastCouponId = insertCoupon(memberId);
		final Long pastReservationId = insertPendingApproval(
			memberId, pastCouponId, "08:59:59");
		insertHeldLog(memberId, pastCouponId, pastReservationId);
		final Long boundaryCouponId = insertCoupon(memberId);
		final Long boundaryReservationId = insertPendingApproval(
			memberId, boundaryCouponId, "09:00:00");
		insertHeldLog(memberId, boundaryCouponId, boundaryReservationId);
		final Long futureCouponId = insertCoupon(memberId);
		final Long futureReservationId = insertPendingApproval(
			memberId, futureCouponId, "09:00:01");
		insertHeldLog(memberId, futureCouponId, futureReservationId);

		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiredCount").value(2))
			.andExpect(jsonPath("$.executedAt").value("2026-08-01T09:00:00"));

		assertThat(reservationStatus(pastReservationId)).isEqualTo("approval_expired");
		assertThat(reservationStatus(boundaryReservationId)).isEqualTo("approval_expired");
		assertThat(reservationStatus(futureReservationId)).isEqualTo("pending_admin_approval");
		assertThat(couponHeldCount(pastCouponId)).isZero();
		assertThat(couponHeldCount(boundaryCouponId)).isZero();
		assertThat(couponHeldCount(futureCouponId)).isEqualTo(1);
		assertThat(releasedLogCount(pastReservationId)).isEqualTo(1);
		assertThat(releasedLogCount(boundaryReservationId)).isEqualTo(1);
		assertThat(releasedActor(pastReservationId)).isEqualTo("system");
	}

	@Test
	void 스케줄러는_동일한_서비스를_멱등하게_실행한다() {
		final Long memberId = insertMember("scheduled-approval-expiry");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertPendingApproval(memberId, couponId, "09:00:00");
		insertHeldLog(memberId, couponId, reservationId);

		scheduler.expirePendingApprovals();
		scheduler.expirePendingApprovals();

		assertThat(reservationStatus(reservationId)).isEqualTo("approval_expired");
		assertThat(couponHeldCount(couponId)).isZero();
		assertThat(releasedLogCount(reservationId)).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 쿠폰_점유_이력이_없으면_예약_만료도_함께_롤백한다() throws Exception {
		final Long memberId = insertMember("broken-approval-expiry");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertPendingApproval(memberId, couponId, "09:00:00");

		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COUPON_HOLD_STATE_CONFLICT"));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_admin_approval");
		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(releasedLogCount(reservationId)).isZero();
	}

	@Test
	void 관리자만_승인대기_만료_작업을_수동_실행할_수_있다() throws Exception {
		mockMvc.perform(post(ENDPOINT))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk());
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '승인 만료 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'approval-expiry-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPendingApproval(Long memberId, Long couponId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', ?, 'pending_admin_approval',
				'coupon', ?, '2026-07-15 09:00:00')
			""", memberId, startTime, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, 'held', 1, '2026-07-15 09:00:00', 'member')
			""", couponId, reservationId, memberId);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?", Integer.class, couponId);
	}

	private int releasedLogCount(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = 'released'
			""", Integer.class, reservationId);
	}

	private String releasedActor(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT actor_type FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = 'released'
			""", String.class, reservationId);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().jwt(token -> token.subject("approval-expiry-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES ('2026-08-01', 'NORMAL', 1)
			""");
	}
}
