package com.horse.coupons.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCouponRegistrationApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 관리자는_회원에게_일반_10회권을_등록한다() throws Exception {
		final Long memberId = insertMember("coupon-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 10)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.memberId").value(memberId))
			.andExpect(jsonPath("$.type").value("general"))
			.andExpect(jsonPath("$.totalCount").value(10))
			.andExpect(jsonPath("$.remainingCount").value(10))
			.andExpect(jsonPath("$.heldCount").value(0))
			.andExpect(jsonPath("$.firstUsedAt").value(nullValue()))
			.andExpect(jsonPath("$.expiresAt").value(nullValue()))
			.andExpect(jsonPath("$.freeChangeUsed").value(false))
			.andExpect(jsonPath("$.status").value("active"))
			.andExpect(jsonPath("$.createdBy").value("coupon-admin"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());

		final CouponRow coupon = findOnlyCoupon(memberId);
		assertCouponDefaults(coupon);
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
	void 열_회가_아닌_쿠폰은_등록할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-count-member");

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt("coupon-admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request("general", 9)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COUPON_INVALID_TOTAL_COUNT"));
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
		return """
			{"type": "%s", "totalCount": %d}
			""".formatted(type, totalCount);
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

	private void assertCouponDefaults(CouponRow coupon) {
		assertThat(coupon.totalCount()).isEqualTo(10);
		assertThat(coupon.remainingCount()).isEqualTo(10);
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
}
