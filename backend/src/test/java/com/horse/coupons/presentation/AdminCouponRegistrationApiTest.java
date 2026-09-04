package com.horse.coupons.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import({TestcontainersConfiguration.class, AdminCouponRegistrationApiTest.FixedClockConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCouponRegistrationApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 관리자는_회원에게_원하는_횟수의_신규_쿠폰을_등록한다() throws Exception {
		final Long memberId = insertMember("coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 20)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.memberId").value(memberId))
			.andExpect(jsonPath("$.type").value("general"))
			.andExpect(jsonPath("$.totalCount").value(20))
			.andExpect(jsonPath("$.remainingCount").value(20))
			.andExpect(jsonPath("$.heldCount").value(0))
			.andExpect(jsonPath("$.firstUsedAt").value(nullValue()))
			.andExpect(jsonPath("$.expiresAt").value(nullValue()))
			.andExpect(jsonPath("$.freeChangeUsed").value(false))
			.andExpect(jsonPath("$.status").value("active"))
			.andExpect(jsonPath("$.createdBy").value("coupon-admin"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());

		final CouponRow coupon = findOnlyCoupon(memberId);
		assertThat(coupon.totalCount()).isEqualTo(20);
		assertThat(coupon.remainingCount()).isEqualTo(20);
		assertCouponUnusedState(coupon);
	}

	@Test
	void 관리자는_기존_사용_쿠폰의_현재_상태와_최초_사용일을_등록한다() throws Exception {
		final Long memberId = insertMember("adopted-coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10, 3, "2026-07-03")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.totalCount").value(10))
			.andExpect(jsonPath("$.remainingCount").value(7))
			.andExpect(jsonPath("$.heldCount").value(0))
			.andExpect(jsonPath("$.firstUsedAt").value("2026-07-03T00:00:00+09:00"))
			.andExpect(jsonPath("$.expiresAt").value("2026-10-03T00:00:00+09:00"))
			.andExpect(jsonPath("$.status").value("active"));

		final CouponRow coupon = findOnlyCoupon(memberId);
		assertThat(coupon.totalCount()).isEqualTo(10);
		assertThat(coupon.remainingCount()).isEqualTo(7);
		assertThat(coupon.firstUsedAt()).isNotNull();
		assertThat(coupon.expiresAt()).isNotNull();
	}

	@Test
	void 관리자는_세_종류의_쿠폰을_각각_등록할_수_있다() throws Exception {
		final Long memberId = insertMember("coupon-types-member");

		for (String type : new String[] {"general", "dressage", "jumping"}) {
			mockMvc.perform(post(endpoint(memberId))
					.with(adminJwt("coupon-admin"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(request(type, 10)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value(type));
		}

		final Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupons WHERE member_id = ?", Integer.class, memberId);
		assertThat(count).isEqualTo(3);
	}

	@Test
	void 성공한_요청마다_쿠폰을_한_장씩_등록한다() throws Exception {
		final Long memberId = insertMember("multiple-coupon-member");

		for (int requestCount = 0; requestCount < 2; requestCount++) {
			mockMvc.perform(post(endpoint(memberId))
					.with(adminJwt("coupon-admin"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(request("general", 10)))
				.andExpect(status().isCreated());
		}

		final Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupons WHERE member_id = ?", Integer.class, memberId);
		assertThat(count).isEqualTo(2);
	}

	@Test
	void 회원은_쿠폰을_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("forbidden-coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10)))
			.andExpect(status().isForbidden());
	}

	@Test
	void 존재하지_않는_회원에게_쿠폰을_등록할_수_없다() throws Exception {
		mockMvc.perform(post(endpoint(999_999L))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void 지원하지_않는_쿠폰_종류는_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-type-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("invalid", 10)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_INVALID_TYPE"));
	}

	@Test
	void 총_횟수가_양수가_아니면_쿠폰을_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-count-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 0)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	@Test
	void 사용된_기존_쿠폰은_최초_사용일_없이_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("missing-first-use-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10, 3, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_INVALID_FIRST_USED_DATE"));
	}

	@Test
	void 사용_횟수가_총_횟수를_넘으면_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-used-count-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 5, 6, "2026-07-03")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_INVALID_USED_COUNT"));
	}

	@Test
	void 미래의_최초_사용일로_기존_쿠폰을_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("future-first-use-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10, 3, "2026-09-05")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_FIRST_USED_DATE_IN_FUTURE"));

		assertThat(couponCount(memberId)).isZero();
	}

	@Test
	void 기존_만료_경계가_지난_쿠폰은_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("expired-adopted-coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10, 3, "2026-06-03")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_EXPIRED_REGISTRATION"));

		assertThat(couponCount(memberId)).isZero();
	}

	@Test
	void 기존_쿠폰은_만료일_당일까지_등록할_수_있다() throws Exception {
		final Long memberId = insertMember("expiry-boundary-adopted-coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10, 3, "2026-06-04")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.expiresAt").value("2026-09-04T00:00:00+09:00"));
	}

	private JwtRequestPostProcessor adminJwt(String subject) {
		return jwt()
			.jwt(token -> token.subject(subject))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private String endpoint(Long memberId) {
		return "/api/admin/members/" + memberId + "/coupons";
	}

	private String request(String type, int totalCount) {
		return request(type, totalCount, 0, null);
	}

	private String request(String type, int totalCount, int usedCount, String firstUsedDate) {
		return """
			{"type": "%s", "totalCount": %d, "usedCount": %d, "firstUsedDate": %s}
			""".formatted(
			type,
			totalCount,
			usedCount,
			firstUsedDate == null ? "null" : "\"" + firstUsedDate + "\"");
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject,
				name,
				phone,
				large_arena_allowed
			) VALUES (?, '테스트 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private CouponRow findOnlyCoupon(Long memberId) {
		return jdbcTemplate.queryForObject("""
			SELECT total_count, remaining_count, held_count, first_used_at, expires_at,
				free_change_used, status, created_by, created_at
			FROM coupons
			WHERE member_id = ?
			""", (resultSet, rowNumber) -> new CouponRow(
			resultSet.getInt("total_count"),
			resultSet.getInt("remaining_count"),
			resultSet.getInt("held_count"),
			resultSet.getObject("first_used_at"),
			resultSet.getObject("expires_at"),
			resultSet.getBoolean("free_change_used"),
			resultSet.getString("status"),
			resultSet.getString("created_by"),
			resultSet.getObject("created_at")), memberId);
	}

	private int couponCount(Long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupons WHERE member_id = ?", Integer.class, memberId);
	}

	private void assertCouponUnusedState(CouponRow coupon) {
		assertThat(coupon.heldCount()).isZero();
		assertThat(coupon.firstUsedAt()).isNull();
		assertThat(coupon.expiresAt()).isNull();
		assertThat(coupon.freeChangeUsed()).isFalse();
		assertThat(coupon.status()).isEqualTo("active");
		assertThat(coupon.createdBy()).isEqualTo("coupon-admin");
		assertThat(coupon.createdAt()).isNotNull();
	}

	private record CouponRow(
		int totalCount,
		int remainingCount,
		int heldCount,
		Object firstUsedAt,
		Object expiresAt,
		boolean freeChangeUsed,
		String status,
		String createdBy,
		Object createdAt
	) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedCouponRegistrationClock() {
			return Clock.fixed(
				Instant.parse("2026-09-03T15:00:00Z"),
				ZoneId.of("Asia/Seoul"));
		}
	}
}
