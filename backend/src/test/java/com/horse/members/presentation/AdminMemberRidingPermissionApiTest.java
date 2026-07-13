package com.horse.members.presentation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberRidingPermissionApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 회원은_특수_클래스_승인을_변경할_수_없다() throws Exception {
		final Long memberId = insertMember("member-role");

		mockMvc.perform(patch(endpoint(memberId))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"dressageApproved": true, "jumpingApproved": false}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_마장마술과_장애물_승인을_각각_변경한다() throws Exception {
		final Long memberId = insertMember("permission-member");

		mockMvc.perform(patch(endpoint(memberId))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"dressageApproved": true, "jumpingApproved": false}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dressageApproved").value(true))
			.andExpect(jsonPath("$.jumpingApproved").value(false))
			.andExpect(jsonPath("$.canUseLargeArena").value(true));

		mockMvc.perform(patch(endpoint(memberId))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"dressageApproved": false, "jumpingApproved": true}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dressageApproved").value(false))
			.andExpect(jsonPath("$.jumpingApproved").value(true))
			.andExpect(jsonPath("$.canUseLargeArena").value(true));
	}

	@Test
	void 모든_특수_승인을_철회하면_초보_회원은_대마장을_이용할_수_없다() throws Exception {
		final Long memberId = insertMember("revoke-member");
		jdbcTemplate.update("UPDATE members SET dressage_approved = TRUE WHERE id = ?", memberId);

		mockMvc.perform(patch(endpoint(memberId))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"dressageApproved": false, "jumpingApproved": false}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dressageApproved").value(false))
			.andExpect(jsonPath("$.jumpingApproved").value(false))
			.andExpect(jsonPath("$.canUseLargeArena").value(false));
	}

	@Test
	void 존재하지_않는_회원의_승인은_변경할_수_없다() throws Exception {
		mockMvc.perform(patch(endpoint(999_999L))
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"dressageApproved": true, "jumpingApproved": true}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private String endpoint(Long memberId) {
		return "/api/admin/members/" + memberId + "/riding-permissions";
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

}
