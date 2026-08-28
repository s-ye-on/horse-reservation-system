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
import java.util.List;

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
class AdminMonthlyRideStatisticsApiTest {

	private static final String ENDPOINT = "/api/admin/reservations/monthly-ride-statistics";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant QUERY_INSTANT = Instant.parse("2026-08-31T15:30:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_서울_조회_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(QUERY_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 기본_현재월은_서울_기준이며_COMPLETED만_lessonDate_월로_집계한다() throws Exception {
		final Long memberId = insertMember("monthly-status-member", "상태 회원");
		insertReservation(memberId, "ROUND_BEGINNER", "2026-09-01", "09:00:00", "completed");
		insertReservation(memberId, "DRESSAGE", "2026-08-31", "09:00:00", "completed");
		insertReservation(memberId, "JUMPING", "2026-10-01", "09:00:00", "completed");
		insertNonCompletedStatuses(memberId);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value("2026-09"))
			.andExpect(jsonPath("$.rideType").value("ALL"))
			.andExpect(jsonPath("$.totalCompletedRideCount").value(1))
			.andExpect(jsonPath("$.topCompletedRideCount").value(1))
			.andExpect(jsonPath("$.leaders.length()").value(1))
			.andExpect(jsonPath("$.leaders[0].memberId").value(memberId))
			.andExpect(jsonPath("$.leaders[0].memberName").value("상태 회원"))
			.andExpect(jsonPath("$.leaders[0].completedRideCount").value(1));
	}

	@Test
	void 조회_종류를_바꾸면_총횟수와_공동_최다_회원을_같은_기준으로_다시_집계한다() throws Exception {
		final Long alphaId = insertMember("monthly-alpha", "가 회원");
		final Long betaId = insertMember("monthly-beta", "나 회원");
		insertCompletedRides(alphaId, "ROUND_BEGINNER", 2, "2026-09-05", 9);
		insertCompletedRides(alphaId, "DRESSAGE", 1, "2026-09-06", 11);
		insertCompletedRides(betaId, "CANTER", 2, "2026-09-07", 13);
		insertCompletedRides(betaId, "JUMPING", 3, "2026-09-08", 15);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-09").param("rideType", "GENERAL"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rideType").value("GENERAL"))
			.andExpect(jsonPath("$.totalCompletedRideCount").value(4))
			.andExpect(jsonPath("$.topCompletedRideCount").value(2))
			.andExpect(jsonPath("$.leaders.length()").value(2))
			.andExpect(jsonPath("$.leaders[0].memberId").value(alphaId))
			.andExpect(jsonPath("$.leaders[0].memberName").value("가 회원"))
			.andExpect(jsonPath("$.leaders[0].completedRideCount").value(2))
			.andExpect(jsonPath("$.leaders[1].memberId").value(betaId))
			.andExpect(jsonPath("$.leaders[1].memberName").value("나 회원"))
			.andExpect(jsonPath("$.leaders[1].completedRideCount").value(2));

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-09").param("rideType", "DRESSAGE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCompletedRideCount").value(1))
			.andExpect(jsonPath("$.leaders[0].memberId").value(alphaId));

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-09").param("rideType", "JUMPING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCompletedRideCount").value(3))
			.andExpect(jsonPath("$.leaders[0].memberId").value(betaId));

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-09").param("rideType", "ALL"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCompletedRideCount").value(8))
			.andExpect(jsonPath("$.topCompletedRideCount").value(5))
			.andExpect(jsonPath("$.leaders.length()").value(1))
			.andExpect(jsonPath("$.leaders[0].memberId").value(betaId));
	}

	@Test
	void 완료_기승이_없는_월과_종류는_0과_빈_공동1위_목록을_반환한다() throws Exception {
		final Long memberId = insertMember("monthly-empty", "빈 통계 회원");
		insertReservation(memberId, "ROUND_TROT", "2026-09-10", "09:00:00", "confirmed");

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-09").param("rideType", "JUMPING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCompletedRideCount").value(0))
			.andExpect(jsonPath("$.topCompletedRideCount").value(0))
			.andExpect(jsonPath("$.leaders").isEmpty());
	}

	@Test
	void 관리자만_조회할_수_있고_잘못된_월과_종류를_거부하며_데이터를_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("monthly-security", "보안 회원");
		insertReservation(memberId, "ROUND_TROT", "2026-09-10", "09:00:00", "completed");
		final Integer beforeCount = reservationCount();

		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("month", "2026-13"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("rideType", "ENDURANCE"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_MONTHLY_RIDE_TYPE"));

		assertThat(reservationCount()).isEqualTo(beforeCount);
	}

	private void insertNonCompletedStatuses(Long memberId) {
		final List<String> statuses = List.of(
			"pending_admin_approval",
			"pending_payment",
			"payment_expired",
			"approval_expired",
			"confirmed",
			"rejected",
			"cancelled",
			"no_show");
		for (int index = 0; index < statuses.size(); index++) {
			insertReservation(memberId, "ROUND_BEGINNER", "2026-09-15", "%02d:00:00".formatted(8 + index), statuses.get(index));
		}
	}

	private void insertCompletedRides(
		Long memberId,
		String ridingClass,
		int count,
		String lessonDate,
		int firstHour
	) {
		for (int index = 0; index < count; index++) {
			insertReservation(
				memberId,
				ridingClass,
				lessonDate,
				"%02d:00:00".formatted(firstHour + index),
				"completed");
		}
	}

	private Long insertMember(String authSubject, String name) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, ?, '010-1111-2222', FALSE)
			""", authSubject, name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertReservation(
		Long memberId,
		String ridingClass,
		String lessonDate,
		String startTime,
		String status
	) {
		final Long couponId = switch (status) {
			case "pending_admin_approval", "approval_expired" -> insertCoupon(memberId);
			default -> null;
		};
		final String paymentSource = couponId == null ? "single_payment" : "coupon";
		final String paymentDueAt = couponId == null ? "2026-09-01 12:00:00" : null;
		final String confirmedAt = switch (status) {
			case "confirmed", "completed", "no_show" -> "2026-09-01 10:00:00";
			default -> null;
		};
		final String rejectedAt = "rejected".equals(status) ? "2026-09-01 10:00:00" : null;
		final String rejectedBy = "rejected".equals(status) ? "monthly-admin" : null;
		final String rejectionReason = "rejected".equals(status) ? "통계 제외 검증" : null;
		final String cancelledAt = "cancelled".equals(status) ? "2026-09-01 10:00:00" : null;
		final String cancellationResponsibility = "cancelled".equals(status) ? "member" : null;

		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at,
				rejected_at, rejected_by, rejection_reason, cancelled_at, cancellation_responsibility
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '2026-09-01 09:00:00', ?, ?, ?, ?, ?, ?)
			""",
			memberId,
			ridingClass,
			lessonDate,
			startTime,
			status,
			paymentSource,
			couponId,
			paymentDueAt,
			confirmedAt,
			rejectedAt,
			rejectedBy,
			rejectionReason,
			cancelledAt,
			cancellationResponsibility);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 0, 'active', 'monthly-test-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Integer reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM member_class_progression_audit_logs");
		jdbcTemplate.update("DELETE FROM members");
	}
}
