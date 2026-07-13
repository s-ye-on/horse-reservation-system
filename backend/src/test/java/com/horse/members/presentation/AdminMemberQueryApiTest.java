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
	void 관리자는_회원_목록을_아이디_순으로_조회한다() throws Exception {
		insertMember("first-member", "첫 회원", 20, 2, 3, false, false);
		insertMember("second-member", "둘째 회원", 1, 4, 5, true, false);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("첫 회원"))
			.andExpect(jsonPath("$[0].generalRideCount").value(20))
			.andExpect(jsonPath("$[0].dressageRideCount").value(2))
			.andExpect(jsonPath("$[0].jumpingRideCount").value(3))
			.andExpect(jsonPath("$[0].dressageApproved").value(false))
			.andExpect(jsonPath("$[0].canUseLargeArena").value(false))
			.andExpect(jsonPath("$[1].name").value("둘째 회원"))
			.andExpect(jsonPath("$[1].dressageApproved").value(true))
			.andExpect(jsonPath("$[1].canUseLargeArena").value(true));
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
				large_arena_allowed
			) VALUES (?, ?, '010-0000-0000', ?, ?, ?, ?, ?, FALSE)
			""", authSubject, name, generalRideCount, dressageRideCount, jumpingRideCount,
			dressageApproved, jumpingApproved);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

}
