package com.horse.members.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberClassProgressionApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	EntityManager entityManager;

	@Test
	void baseline_설정_변경_해제는_Anchor를_계산하고_관리_시작_경계를_유지하며_감사한다() throws Exception {
		final long memberId = insertExistingMember("baseline-member", 0);

		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"LARGE_ARENA_TROT","reason":"기존 경력 인정"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalRideCount").value(0))
			.andExpect(jsonPath("$.progressionValue").value(26))
			.andExpect(jsonPath("$.progressionClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.effectiveClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.progressionBaselineActualRideCount").value(0));
		final String startedAt = progressionStartedAt(memberId);

		updateGeneralRideCount(memberId, 10);
		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"CANTER_BEGINNER","reason":"baseline 교정"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionValue").value(70))
			.andExpect(jsonPath("$.progressionBaselineActualRideCount").value(10));

		mockMvc.perform(delete(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"잘못된 baseline 제거\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionValue").value(10))
			.andExpect(jsonPath("$.progressionBaselineClass").doesNotExist());

		assertThat(progressionStartedAt(memberId)).isEqualTo(startedAt);
		assertThat(auditActions(memberId)).containsExactly(
			"PROGRESSION_INITIALIZED",
			"BASELINE_SET",
			"BASELINE_CHANGED",
			"BASELINE_REMOVED");
	}

	@Test
	void 특수_승인은_현재_progression의_부족분만_인정하고_해제와_재승인에서_중복하지_않는다() throws Exception {
		final long memberId = insertManagedMember("special-credit-member", 20);

		changeApprovals(memberId, true, false, "마장마술 승인")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalRideCount").value(20))
			.andExpect(jsonPath("$.specialApprovalProgressionCredit").value(6))
			.andExpect(jsonPath("$.progressionValue").value(26));

		changeApprovals(memberId, false, false, "마장마술 승인 해제")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.specialApprovalProgressionCredit").value(6));
		changeApprovals(memberId, false, true, "장애물 승인")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.specialApprovalProgressionCredit").value(6));

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM member_class_progression_audit_logs
			WHERE member_id = ? AND action = 'SPECIAL_APPROVAL_CHANGED'
			""", Integer.class, memberId)).isEqualTo(3);
	}

	@Test
	void promotion_hold는_progression_이하의_상한이며_특수_승인과_상호_배타다() throws Exception {
		final long memberId = insertManagedMember("promotion-hold-member", 100);

		mockMvc.perform(put(endpoint(memberId, "/promotion-hold"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"promotionHoldClass":"LARGE_ARENA_TROT","reason":"안전상 보류"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionClass").value("CANTER"))
			.andExpect(jsonPath("$.effectiveClass").value("LARGE_ARENA_TROT"));

		changeApprovals(memberId, true, false, "마장마술 승인")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_CLASS_POLICY_CONFLICT"));

		updateGeneralRideCount(memberId, 101);
		mockMvc.perform(delete(endpoint(memberId, "/promotion-hold"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"보류 해제\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionValue").value(101))
			.andExpect(jsonPath("$.effectiveClass").value("CANTER"));
	}

	@Test
	void 잘못된_특수_승인_credit은_모든_승인을_해제한_뒤_하향_교정한다() throws Exception {
		final long memberId = insertManagedMember("credit-correction-member", 20);
		changeApprovals(memberId, true, false, "승인").andExpect(status().isOk());

		mockMvc.perform(put(endpoint(memberId, "/special-approval-credit"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"specialApprovalProgressionCredit":0,"reason":"잘못된 인정 교정"}
					"""))
			.andExpect(status().isConflict());

		changeApprovals(memberId, false, false, "승인 해제").andExpect(status().isOk());
		mockMvc.perform(put(endpoint(memberId, "/special-approval-credit"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"specialApprovalProgressionCredit":0,"reason":"잘못된 인정 교정"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressionValue").value(20));
	}

	@Test
	void 회원은_progression을_변경할_수_없고_관리자_사유는_필수다() throws Exception {
		final long memberId = insertManagedMember("progression-role-member", 10);

		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"LARGE_ARENA_TROT","reason":"권한 없음"}
					"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"LARGE_ARENA_TROT","reason":" "}
					"""))
			.andExpect(status().isBadRequest());
	}

	private org.springframework.test.web.servlet.ResultActions changeApprovals(
		long memberId,
		boolean dressageApproved,
		boolean jumpingApproved,
		String reason
	) throws Exception {
		return mockMvc.perform(patch("/api/admin/members/{memberId}/riding-permissions", memberId)
			.with(adminJwt())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "dressageApproved": %s,
				  "jumpingApproved": %s,
				  "reason": "%s"
				}
				""".formatted(dressageApproved, jumpingApproved, reason)));
	}

	private long insertExistingMember(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, general_ride_count)
			VALUES (?, '기존 회원', '010-0000-0000', ?)
			""", authSubject, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertManagedMember(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, progression_management_started_at
			) VALUES (?, '관리 회원', '010-0000-0000', ?, CURRENT_TIMESTAMP(6))
			""", authSubject, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void updateGeneralRideCount(long memberId, int actualCount) {
		entityManager.flush();
		jdbcTemplate.update(
			"UPDATE members SET general_ride_count = ? WHERE id = ?",
			actualCount,
			memberId);
		entityManager.clear();
	}

	private String progressionStartedAt(long memberId) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("""
			SELECT CAST(progression_management_started_at AS CHAR)
			FROM members
			WHERE id = ?
			""", String.class, memberId);
	}

	private java.util.List<String> auditActions(long memberId) {
		return jdbcTemplate.queryForList("""
			SELECT action
			FROM member_class_progression_audit_logs
			WHERE member_id = ?
			ORDER BY id
			""", String.class, memberId);
	}

	private String endpoint(long memberId, String suffix) {
		return "/api/admin/members/" + memberId + "/class-progression" + suffix;
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("m32-06-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}
}
