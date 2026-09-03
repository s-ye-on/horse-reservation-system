package com.horse.families.presentation;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFamilyGroupQueryApiTest {

	private static final String ENDPOINT = "/api/admin/family-groups";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 비인증과_회원은_가족_조회_API를_호출할_수_없다() throws Exception {
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_이름과_상태로_그룹을_검색하고_활성_구성원_수를_Page로_조회한다() throws Exception {
		final long activeGroupId = insertGroup("푸른 가족", "ACTIVE");
		final long otherGroupId = insertGroup("다른 가족", "ACTIVE");
		insertGroup("푸른 해제 가족", "DISSOLVED");
		insertMembership(activeGroupId, insertMember("active-family-member", "첫 회원"), null);
		insertMembership(activeGroupId, insertMember("ended-family-member", "종료 회원"), "2026-08-15 10:00:00");
		insertMembership(otherGroupId, insertMember("other-family-member", "다른 회원"), null);

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("query", "푸른")
				.param("status", "ACTIVE")
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].groupId").value(activeGroupId))
			.andExpect(jsonPath("$.content[0].activeMemberCount").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void 관리자는_현재_구성원과_추가_가능한_회원만_각각_Page로_조회한다() throws Exception {
		final long groupId = insertGroup("구성원 가족", "ACTIVE");
		final long activeMemberId = insertMember("assigned-member", "배정 회원");
		final long candidateMemberId = insertMember("candidate-member", "후보 회원");
		insertMembership(groupId, activeMemberId, null);

		mockMvc.perform(get(ENDPOINT + "/{groupId}/members", groupId)
				.with(adminJwt())
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].memberId").value(activeMemberId))
			.andExpect(jsonPath("$.content[0].name").value("배정 회원"))
			.andExpect(jsonPath("$.content[0].joinedAt").value("2026-08-15T10:00:00+09:00"));

		mockMvc.perform(get(ENDPOINT + "/member-candidates")
				.with(adminJwt())
				.param("query", "후보")
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].memberId").value(candidateMemberId))
			.andExpect(jsonPath("$.content[0].name").value("후보 회원"));
	}

	@Test
	void 관리자는_가족_감사를_최신순_Page로_조회한다() throws Exception {
		final long groupId = insertGroup("감사 가족", "ACTIVE");
		final long memberId = insertMember("audit-member", "감사 회원");
		insertAudit(groupId, null, "GROUP_CREATED", "그룹 생성", "2026-08-15 09:00:00");
		insertAudit(groupId, memberId, "MEMBER_ADDED", "구성원 추가", "2026-08-15 10:00:00");

		mockMvc.perform(get(ENDPOINT + "/{groupId}/audit-logs", groupId)
				.with(adminJwt())
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].action").value("MEMBER_ADDED"))
			.andExpect(jsonPath("$.content[0].memberId").value(memberId))
			.andExpect(jsonPath("$.content[0].memberName").value("감사 회원"))
			.andExpect(jsonPath("$.content[0].reason").value("구성원 추가"))
			.andExpect(jsonPath("$.content[0].actorAuthSubject").value("admin-family"))
			.andExpect(jsonPath("$.content[0].occurredAt").value("2026-08-15T10:00:00+09:00"))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	void 잘못된_페이지와_존재하지_않는_그룹은_공통_오류로_거부한다() throws Exception {
		mockMvc.perform(get(ENDPOINT).with(adminJwt()).param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
		mockMvc.perform(get(ENDPOINT + "/{groupId}/members", 999_999).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FAMILY_GROUP_NOT_FOUND"));
	}

	private long insertGroup(String name, String status) {
		jdbcTemplate.update("""
			INSERT INTO family_groups (name, status, dissolved_at)
			VALUES (?, ?, IF(? = 'DISSOLVED', '2026-08-15 10:00:00', NULL))
			""", name, status, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertMember(String authSubject, String name) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, ?, '010-1234-5678')
			""", authSubject, name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId, String endedAt) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id, joined_at, ended_at)
			VALUES (?, ?, '2026-08-15 10:00:00', ?)
			""", groupId, memberId, endedAt);
	}

	private void insertAudit(long groupId, Long memberId, String action, String reason, String createdAt) {
		final String fromState = "GROUP_CREATED".equals(action) ? null : "{\"status\":\"NONE\"}";
		jdbcTemplate.update("""
			INSERT INTO family_group_audit_logs (
				family_group_id, member_id, action, from_state, to_state,
				actor_auth_subject, reason, created_at
			) VALUES (?, ?, ?, CAST(? AS JSON), JSON_OBJECT('status', 'ACTIVE'), 'admin-family', ?, ?)
			""", groupId, memberId, action, fromState, reason, createdAt);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}
}
