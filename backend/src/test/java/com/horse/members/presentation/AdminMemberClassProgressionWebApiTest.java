package com.horse.members.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.jayway.jsonpath.JsonPath;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberClassProgressionWebApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void baseline_preview는_변경_전후_예상_class를_반환하고_상태와_감사를_변경하지_않는다() throws Exception {
		final long memberId = insertManagedMember("m32-08-preview", 10, false, false);

		mockMvc.perform(post(endpoint(memberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"SET_BASELINE","baselineClass":"CANTER_BEGINNER"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stateToken").isNotEmpty())
			.andExpect(jsonPath("$.current.progressionValue").value(10))
			.andExpect(jsonPath("$.current.progressionClass").value("ROUND_TROT"))
			.andExpect(jsonPath("$.expected.progressionValue").value(70))
			.andExpect(jsonPath("$.expected.progressionClass").value("CANTER_BEGINNER"))
			.andExpect(jsonPath("$.expected.effectiveClass").value("CANTER_BEGINNER"))
			.andExpect(jsonPath("$.expected.baselineActualRideCount").value(10));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT progression_baseline_class FROM members WHERE id = ?",
			String.class,
			memberId)).isNull();
		assertThat(auditCount(memberId)).isZero();
	}

	@Test
	void 최초_baseline_preview는_미초기화_현재_상태와_초기화_후_예상값을_함께_반환한다() throws Exception {
		final long memberId = insertUnmanagedMember("m32-08-first-baseline", 10);

		mockMvc.perform(post(endpoint(memberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"SET_BASELINE","baselineClass":"CANTER_BEGINNER"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.progressionValue").value(10))
			.andExpect(jsonPath("$.current.baselineClass").doesNotExist())
			.andExpect(jsonPath("$.expected.progressionValue").value(70))
			.andExpect(jsonPath("$.expected.baselineActualRideCount").value(10));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT progression_management_started_at FROM members WHERE id = ?",
			String.class,
			memberId)).isNull();
		assertThat(auditCount(memberId)).isZero();
	}

	@Test
	void preview_후_회원_상태가_바뀌면_이전_상태_Token의_Command를_거부한다() throws Exception {
		final long memberId = insertManagedMember("m32-08-stale-preview", 10, false, false);
		final String previewBody = mockMvc.perform(post(endpoint(memberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"SET_BASELINE","baselineClass":"LARGE_ARENA_TROT"}
					"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		final String staleStateToken = JsonPath.read(previewBody, "$.stateToken");

		changeBaseline(memberId, "LARGE_ARENA_TROT", "동시 관리자 변경");

		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.header(HttpHeaders.IF_MATCH, staleStateToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"CANTER_BEGINNER","reason":"오래된 예상 결과 적용"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_CLASS_STATE_CONFLICT"));

		assertThat(auditCount(memberId)).isEqualTo(1);
	}

	@Test
	void preview는_상호_배타와_필수_후보와_횟수_보정_경계를_Domain과_같이_검증한다() throws Exception {
		final long approvedMemberId = insertManagedMember("m32-08-approved", 20, true, false);
		final long managedMemberId = insertManagedMember("m32-08-adjust", 20, false, false);

		mockMvc.perform(post(endpoint(approvedMemberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"SET_PROMOTION_HOLD","promotionHoldClass":"ROUND_TROT"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_CLASS_POLICY_CONFLICT"));
		mockMvc.perform(post(endpoint(managedMemberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"SET_BASELINE\"}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(endpoint(managedMemberId, "/preview"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"ADJUST_RIDE_COUNT","rideCountDelta":2}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.actualCompletedRideCount").value(20))
			.andExpect(jsonPath("$.expected.actualCompletedRideCount").value(22))
			.andExpect(jsonPath("$.expected.progressionValue").value(22));
	}

	@Test
	void 감사_Page는_최신순_전후_snapshot과_관리자_사유를_반환한다() throws Exception {
		final long memberId = insertManagedMember("m32-08-audit", 10, false, false);
		changeBaseline(memberId, "LARGE_ARENA_TROT", "기존 경력 인정");
		changeBaseline(memberId, "CANTER_BEGINNER", "경력 정보 교정");

		mockMvc.perform(get(endpoint(memberId, "/audit-logs"))
				.with(adminJwt())
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].action").value("BASELINE_CHANGED"))
			.andExpect(jsonPath("$.content[0].fromState.progressionClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.content[0].toState.progressionClass").value("CANTER_BEGINNER"))
			.andExpect(jsonPath("$.content[0].actorAuthSubject").value("m32-08-admin"))
			.andExpect(jsonPath("$.content[0].reason").value("경력 정보 교정"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	void 회원은_preview와_감사를_조회할_수_없다() throws Exception {
		final long memberId = insertManagedMember("m32-08-role", 10, false, false);

		mockMvc.perform(post(endpoint(memberId, "/preview"))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"ADJUST_RIDE_COUNT\",\"rideCountDelta\":1}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(endpoint(memberId, "/audit-logs"))
				.with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	private long insertManagedMember(
		String authSubject,
		int actualCount,
		boolean dressageApproved,
		boolean jumpingApproved
	) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count,
				dressage_approved, jumping_approved, progression_management_started_at,
				special_approval_progression_credit
			) VALUES (?, '관리 회원', '010-0000-0000', ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
			""", authSubject, actualCount, dressageApproved, jumpingApproved,
			dressageApproved || jumpingApproved ? Math.max(0, 26 - actualCount) : 0);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertUnmanagedMember(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count,
				dressage_approved, jumping_approved
			) VALUES (?, '미초기화 회원', '010-0000-0000', ?, false, false)
			""", authSubject, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void changeBaseline(long memberId, String baselineClass, String reason) throws Exception {
		mockMvc.perform(put(endpoint(memberId, "/baseline"))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"baselineClass":"%s","reason":"%s"}
					""".formatted(baselineClass, reason)))
			.andExpect(status().isOk());
	}

	private int auditCount(long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM member_class_progression_audit_logs WHERE member_id = ?",
			Integer.class,
			memberId);
	}

	private String endpoint(long memberId, String suffix) {
		return "/api/admin/members/" + memberId + "/class-progression" + suffix;
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("m32-08-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}
}
