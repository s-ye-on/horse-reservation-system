package com.horse.members.presentation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberQueryApiTest {

	private static final String ENDPOINT = "/api/admin/members";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 회원은_관리자_회원_목록을_조회할_수_없다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_회원_목록을_최신_등록순과_아이디_역순으로_조회한다() throws Exception {
		final Long firstId = insertMember("first-member", "첫 회원", 20, 2, 3, false, false);
		final Long secondId = insertMember("second-member", "둘째 회원", 1, 4, 5, true, false);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].id").value(secondId))
			.andExpect(jsonPath("$.content[0].name").value("둘째 회원"))
			.andExpect(jsonPath("$.content[0].dressageApproved").value(true))
			.andExpect(jsonPath("$.content[0].canUseLargeArena").value(true))
			.andExpect(jsonPath("$.content[1].id").value(firstId))
			.andExpect(jsonPath("$.content[1].generalRideCount").value(20))
			.andExpect(jsonPath("$.content[1].dressageRideCount").value(2))
			.andExpect(jsonPath("$.content[1].jumpingRideCount").value(3))
			.andExpect(jsonPath("$.content[1].dressageApproved").value(false))
			.andExpect(jsonPath("$.content[1].canUseLargeArena").value(false))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void 관리자는_첫_중간_마지막과_빈_페이지를_조회한다() throws Exception {
		final Long firstId = insertMember("page-first", "첫 회원", 0, 0, 0, false, false);
		final Long secondId = insertMember("page-second", "둘째 회원", 0, 0, 0, false, false);
		final Long thirdId = insertMember("page-third", "셋째 회원", 0, 0, 0, false, false);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "0").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(thirdId))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "1").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(secondId))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "2").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(firstId))
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "3").param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void 잘못된_페이지_조건은_공통_오류로_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	@Test
	void 관리자는_회원_상세를_조회한다() throws Exception {
		final Long memberId = insertMember("large-arena-member", "대마장 회원", 21, 6, 7, false, false);

		mockMvc.perform(get(ENDPOINT + "/{memberId}", memberId).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(memberId))
			.andExpect(jsonPath("$.name").value("대마장 회원"))
			.andExpect(jsonPath("$.phone").value("010-0000-0000"))
			.andExpect(jsonPath("$.generalRideCount").value(21))
			.andExpect(jsonPath("$.dressageRideCount").value(6))
			.andExpect(jsonPath("$.jumpingRideCount").value(7))
			.andExpect(jsonPath("$.jumpingApproved").value(false))
			.andExpect(jsonPath("$.canUseLargeArena").value(true));
	}

	@Test
	void 존재하지_않는_회원_상세는_찾을_수_없다() throws Exception {
		mockMvc.perform(get(ENDPOINT + "/{memberId}", 999_999L).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private Long insertMember(
		String authSubject,
		String name,
		int generalRideCount,
		int dressageRideCount,
		int jumpingRideCount,
		boolean dressageApproved,
		boolean jumpingApproved
	) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject,
				name,
				phone,
				general_ride_count,
				dressage_ride_count,
				jumping_ride_count,
				dressage_approved,
				jumping_approved,
				large_arena_allowed,
				progression_management_started_at,
				special_approval_progression_credit
			) VALUES (
				?, ?, '010-0000-0000', ?, ?, ?, ?, ?, FALSE,
				CURRENT_TIMESTAMP(6), ?
			)
			""", authSubject, name, generalRideCount, dressageRideCount, jumpingRideCount,
			dressageApproved, jumpingApproved,
			dressageApproved || jumpingApproved ? Math.max(0, 26 - generalRideCount) : 0);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

}
