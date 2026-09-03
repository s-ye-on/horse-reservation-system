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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAvailableRidingClassesApiTest {

	private static final String ENDPOINT = "/api/me/eligible-classes";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 인증되지_않은_요청은_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 인증_주체와_일치하는_회원이_없으면_회원을_찾을_수_없다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("missing-member"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void 현재_등급과_그보다_낮은_일반_클래스를_조회한다() throws Exception {
		insertMember("round-trot-member", 6, false, false);

		mockMvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("round-trot-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentGeneralGrade").value("ROUND_TROT"))
			.andExpect(jsonPath("$.canUseLargeArena").value(false))
			.andExpect(jsonPath("$.availableRidingClasses.length()").value(3))
			.andExpect(jsonPath("$.availableRidingClasses[0]").value("FIRST_RIDE"))
			.andExpect(jsonPath("$.availableRidingClasses[1]").value("ROUND_BEGINNER"))
			.andExpect(jsonPath("$.availableRidingClasses[2]").value("ROUND_TROT"));
	}

	@Test
	void 특수_승인_회원은_대마장_일반_클래스와_승인된_특수_클래스를_조회한다() throws Exception {
		insertMember("dressage-member", 1, true, false);

		mockMvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("dressage-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentGeneralGrade").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.progressionValue").value(26))
			.andExpect(jsonPath("$.progressionClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.effectiveClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.dressageApproved").value(true))
			.andExpect(jsonPath("$.jumpingApproved").value(false))
			.andExpect(jsonPath("$.canUseLargeArena").value(true))
			.andExpect(jsonPath("$.availableRidingClasses.length()").value(6))
			.andExpect(jsonPath("$.availableRidingClasses[4]").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.availableRidingClasses[5]").value("DRESSAGE"));
	}

	@Test
	void 구보초보와_구보_threshold에_도달한_회원은_해당_일반_클래스를_조회한다() throws Exception {
		insertMember("canter-member", 100, false, false);

		mockMvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("canter-member"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionValue").value(100))
			.andExpect(jsonPath("$.progressionClass").value("CANTER"))
			.andExpect(jsonPath("$.effectiveClass").value("CANTER"))
			.andExpect(jsonPath("$.availableRidingClasses.length()").value(7))
			.andExpect(jsonPath("$.availableRidingClasses[5]").value("CANTER_BEGINNER"))
			.andExpect(jsonPath("$.availableRidingClasses[6]").value("CANTER"));
	}

	private void insertMember(
		String authSubject,
		int generalRideCount,
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
				?, '테스트 회원', '010-0000-0000', ?, 0, 0, ?, ?, FALSE,
				CURRENT_TIMESTAMP(6), ?
			)
			""",
			authSubject,
			generalRideCount,
			dressageApproved,
			jumpingApproved,
			dressageApproved || jumpingApproved ? Math.max(0, 26 - generalRideCount) : 0);
	}

}
