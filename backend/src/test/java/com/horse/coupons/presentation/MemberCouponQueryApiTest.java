package com.horse.coupons.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberCouponQueryApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 회원은_자신의_쿠폰_목록만_조회한다() throws Exception {
		final Long memberId = insertMember("coupon-query-member");
		final Long otherMemberId = insertMember("coupon-query-other");
		final Long couponId = insertCoupon(memberId, "general", 8, 2);
		insertCoupon(otherMemberId, "jumping", 10, 0);

		mockMvc.perform(get("/api/me/coupons").with(jwt().jwt(token -> token.subject("coupon-query-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.content[0].couponId").value(couponId))
			.andExpect(jsonPath("$.content[0].type").value("general"))
			.andExpect(jsonPath("$.content[0].totalCount").value(10))
			.andExpect(jsonPath("$.content[0].remainingCount").value(8))
			.andExpect(jsonPath("$.content[0].heldCount").value(2))
			.andExpect(jsonPath("$.content[0].availableCount").value(6))
			.andExpect(jsonPath("$.content[0].freeChangeUsed").value(false))
			.andExpect(jsonPath("$.content[0].status").value("active"));
	}

	@Test
	void 회원은_메모를_제외한_자신의_쿠폰_사용_내역만_조회한다() throws Exception {
		final Long memberId = insertMember("usage-query-member");
		final Long otherMemberId = insertMember("usage-query-other");
		final Long couponId = insertCoupon(memberId, "general", 9, 1);
		final Long reservationId = insertReservation(memberId, couponId);
		insertUsageLog(couponId, reservationId, memberId, "held", 1, "member", "내부 메모");
		final Long otherCouponId = insertCoupon(otherMemberId, "dressage", 10, 0);
		insertUsageLog(otherCouponId, null, otherMemberId, "expired", -10, "system", "타 회원 메모");

		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject("usage-query-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.content[0].couponId").value(couponId))
			.andExpect(jsonPath("$.content[0].reservationId").value(reservationId))
			.andExpect(jsonPath("$.content[0].action").value("held"))
			.andExpect(jsonPath("$.content[0].countDelta").value(1))
			.andExpect(jsonPath("$.content[0].occurredAt").value("2026-07-14T10:00:00+09:00"))
			.andExpect(jsonPath("$.content[0].actorType").value("member"))
			.andExpect(jsonPath("$.content[0].memo").doesNotExist());
	}

	@Test
	void 쿠폰과_사용_내역은_첫_중간_마지막과_빈_페이지를_안정적으로_조회한다() throws Exception {
		final String subject = "coupon-page-member";
		final Long memberId = insertMember(subject);
		final Long firstCouponId = insertCoupon(memberId, "general", 10, 0);
		final Long secondCouponId = insertCoupon(memberId, "dressage", 10, 0);
		final Long thirdCouponId = insertCoupon(memberId, "jumping", 10, 0);
		final Long reservationId = insertReservation(memberId, firstCouponId);
		final Long firstUsageId = insertUsageLog(
			firstCouponId, reservationId, memberId, "held", 1, "member", null);
		final Long secondUsageId = insertUsageLog(
			firstCouponId, reservationId, memberId, "released", -1, "member", null);
		final Long thirdUsageId = insertUsageLog(
			firstCouponId, reservationId, memberId, "held", 1, "member", null);

		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].couponId").value(thirdCouponId))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "1")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].couponId").value(secondCouponId))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "2")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].couponId").value(firstCouponId))
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "3")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty());

		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].usageLogId").value(thirdUsageId))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "1")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].usageLogId").value(secondUsageId))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "2")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].usageLogId").value(firstUsageId))
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "3")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void 잘못된_쿠폰_페이지는_공통_오류로_거부한다() throws Exception {
		final String subject = "coupon-invalid-page-member";
		insertMember(subject);

		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject(subject)))
				.param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	@Test
	void 쿠폰과_사용_내역_조회는_상태를_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("read-only-coupon-member");
		final Long couponId = insertCoupon(memberId, "general", 8, 2);
		final Long reservationId = insertReservation(memberId, couponId);
		insertUsageLog(couponId, reservationId, memberId, "held", 1, "member", null);
		final List<String> before = snapshots(memberId);

		mockMvc.perform(get("/api/me/coupons")
				.with(jwt().jwt(token -> token.subject("read-only-coupon-member"))))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/me/coupon-usage-logs")
				.with(jwt().jwt(token -> token.subject("read-only-coupon-member"))))
			.andExpect(status().isOk());

		assertThat(snapshots(memberId)).isEqualTo(before);
	}

	@Test
	void 인증되지_않은_사용자는_쿠폰_정보를_조회할_수_없다() throws Exception {
		mockMvc.perform(get("/api/me/coupons"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/me/coupon-usage-logs"))
			.andExpect(status().isUnauthorized());
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '조회 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private Long insertCoupon(Long memberId, String type, int remainingCount, int heldCount) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, ?, 10, ?, ?, 'query-admin')
			""", memberId, type, remainingCount, heldCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-10', '09:00:00',
				'pending_admin_approval', 'coupon', ?, '2026-07-14 10:00:00')
			""", memberId, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertUsageLog(
		Long couponId,
		Long reservationId,
		Long memberId,
		String action,
		int countDelta,
		String actorType,
		String memo
	) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
			) VALUES (?, ?, ?, ?, ?, '2026-07-14 10:00:00', ?, ?)
			""", couponId, reservationId, memberId, action, countDelta, actorType, memo);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private List<String> snapshots(Long memberId) {
		return jdbcTemplate.queryForList("""
			SELECT CONCAT('coupon:', id, ':', remaining_count, ':', held_count, ':', status)
			FROM coupons
			WHERE member_id = ?
			UNION ALL
			SELECT CONCAT('log:', id, ':', action, ':', count_delta)
			FROM coupon_usage_logs
			WHERE member_id = ?
			ORDER BY 1
			""", String.class, memberId, memberId);
	}
}
