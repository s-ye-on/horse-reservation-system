package com.horse.families.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;
import com.jayway.jsonpath.JsonPath;

import static org.mockito.BDDMockito.given;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFamilyGroupCommandApiTest {

	private static final String ENDPOINT = "/api/admin/family-groups";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 시간_고정() {
		given(clock.instant()).willReturn(Instant.parse("2026-08-14T01:00:00Z"));
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
	}

	@Test
	void 비인증과_회원은_가족_Command_API를_호출할_수_없다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("김 가족", "가족 등록")))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("김 가족", "가족 등록")))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_중복_이름의_빈_ACTIVE_그룹을_생성하고_필수_감사를_남긴다() throws Exception {
		final long firstGroupId = createGroup("같은 가족", "첫 그룹 생성");
		final long secondGroupId = createGroup("같은 가족", "둘째 그룹 생성");

		assertThat(firstGroupId).isNotEqualTo(secondGroupId);
		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_groups
			WHERE name = '같은 가족' AND status = 'ACTIVE'
			""")).isEqualTo(2);
		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE family_group_id IN (?, ?)
			""", firstGroupId, secondGroupId)).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT CONCAT(action, '|', actor_auth_subject, '|', reason)
			FROM family_group_audit_logs
			WHERE family_group_id = ?
			""", String.class, firstGroupId))
			.isEqualTo("GROUP_CREATED|admin-family|첫 그룹 생성");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.status'))
			FROM family_group_audit_logs
			WHERE family_group_id = ?
			""", String.class, firstGroupId)).isEqualTo("ACTIVE");
	}

	@Test
	void 그룹_이름과_변경_사유는_필수다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest(" ", "가족 등록")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("김 가족", " ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
	}

	@Test
	void 회원은_하나의_활성_가족에만_추가되고_전후_감사를_남긴다() throws Exception {
		final long memberId = createMember("family-member-1");
		final long firstGroupId = createGroup("첫 가족", "첫 그룹 생성");
		final long secondGroupId = createGroup("둘째 가족", "둘째 그룹 생성");

		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", firstGroupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(memberId, "첫 가족 가입")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.groupId").value(firstGroupId))
			.andExpect(jsonPath("$.memberId").value(memberId))
			.andExpect(jsonPath("$.joinedAt").value("2026-08-14T10:00:00+09:00"))
			.andExpect(jsonPath("$.endedAt").doesNotExist());

		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", secondGroupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(memberId, "중복 가입 시도")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FAMILY_MEMBER_ALREADY_ASSIGNED"));

		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE member_id = ? AND ended_at IS NULL
			""", memberId)).isOne();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT CONCAT(
				JSON_UNQUOTE(JSON_EXTRACT(from_state, '$.status')),
				'->',
				JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.status'))
			)
			FROM family_group_audit_logs
			WHERE family_group_id = ? AND action = 'MEMBER_ADDED'
			""", String.class, firstGroupId)).isEqualTo("NONE->ACTIVE");
	}

	@Test
	void 제거와_다른_가족_추가는_독립_Command로_이력을_보존한다() throws Exception {
		final long memberId = createMember("family-member-2");
		final long firstGroupId = createGroup("기존 가족", "첫 그룹 생성");
		final long secondGroupId = createGroup("새 가족", "둘째 그룹 생성");
		addMember(firstGroupId, memberId, "기존 가족 가입");

		mockMvc.perform(delete(ENDPOINT + "/{groupId}/members/{memberId}", firstGroupId, memberId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(reasonRequest("기존 가족에서 제거")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endedAt").value("2026-08-14T10:00:00+09:00"));

		addMember(secondGroupId, memberId, "새 가족 가입");

		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships WHERE member_id = ?
			""", memberId)).isEqualTo(2);
		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE member_id = ? AND ended_at IS NULL
			""", memberId)).isOne();
		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_group_audit_logs
			WHERE family_group_id = ? AND action = 'MEMBER_REMOVED'
			  AND reason = '기존 가족에서 제거'
			""", firstGroupId)).isOne();
	}

	@Test
	void 그룹_해제는_모든_활성_membership을_종료하고_비가역_감사를_남긴다() throws Exception {
		final long firstMemberId = createMember("family-member-3");
		final long secondMemberId = createMember("family-member-4");
		final long groupId = createGroup("해제 가족", "그룹 생성");
		addMember(groupId, firstMemberId, "첫 회원 가입");
		addMember(groupId, secondMemberId, "둘째 회원 가입");

		mockMvc.perform(post(ENDPOINT + "/{groupId}/dissolution", groupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(reasonRequest("가족 관계 종료")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISSOLVED"))
			.andExpect(jsonPath("$.dissolvedAt").value("2026-08-14T10:00:00+09:00"));
		entityManager.flush();

		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE family_group_id = ? AND ended_at IS NULL
			""", groupId)).isZero();
		assertThat(queryCount("""
			SELECT COUNT(*) FROM family_memberships
			WHERE family_group_id = ? AND ended_at = '2026-08-14 10:00:00'
			""", groupId)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT JSON_LENGTH(JSON_EXTRACT(from_state, '$.activeMemberIds'))
			FROM family_group_audit_logs
			WHERE family_group_id = ? AND action = 'GROUP_DISSOLVED'
			""", Integer.class, groupId)).isEqualTo(2);

		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", groupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(firstMemberId, "해제 그룹 재가입")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FAMILY_GROUP_NOT_ACTIVE"));

		mockMvc.perform(post(ENDPOINT + "/{groupId}/dissolution", groupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(reasonRequest("다시 해제")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FAMILY_GROUP_NOT_ACTIVE"));
	}

	@Test
	void 존재하지_않는_그룹과_회원은_공통_오류_계약으로_거부한다() throws Exception {
		final long groupId = createGroup("조회 가족", "그룹 생성");

		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", 999_999)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(999_999, "회원 가입")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("FAMILY_GROUP_NOT_FOUND"));

		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", groupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(999_999, "회원 가입")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private long createGroup(String name, String reason) throws Exception {
		final MvcResult result = mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest(name, reason)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn();
		return ((Number) JsonPath.read(
			result.getResponse().getContentAsString(),
			"$.groupId")).longValue();
	}

	private void addMember(long groupId, long memberId, String reason) throws Exception {
		mockMvc.perform(post(ENDPOINT + "/{groupId}/members", groupId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(addMemberRequest(memberId, reason)))
			.andExpect(status().isCreated());
	}

	private long createMember(String authSubject) {
		return memberRepository.saveAndFlush(
			Member.create(authSubject, "가족 회원", "010-0000-0000")).getId();
	}

	private long queryCount(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Long.class, arguments);
	}

	private String createRequest(String name, String reason) {
		return """
			{
			  "name": "%s",
			  "reason": "%s"
			}
			""".formatted(name, reason);
	}

	private String addMemberRequest(long memberId, String reason) {
		return """
			{
			  "memberId": %d,
			  "reason": "%s"
			}
			""".formatted(memberId, reason);
	}

	private String reasonRequest(String reason) {
		return """
			{
			  "reason": "%s"
			}
			""".formatted(reason);
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("admin-family"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt()
			.jwt(token -> token.subject("member-family"))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}
}
