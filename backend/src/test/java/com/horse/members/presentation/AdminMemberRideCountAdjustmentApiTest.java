package com.horse.members.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminMemberRideCountAdjustmentApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 기존_테스트_데이터를_정리한다() {
		clearTestData();
	}

	@AfterEach
	void 생성한_테스트_데이터를_정리한다() {
		clearTestData();
	}

	@Test
	void 관리자는_delta로_actual_count만_보정하고_전후_progression과_사유를_감사한다() throws Exception {
		final long memberId = insertManagedMemberWithProgressionInputs("m32-07-api-member", 10);
		final LocalDateTime startedAt = progressionStartedAt(memberId);

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"delta":2,"reason":"완료 집계 2회 누락 정정"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalRideCount").value(12))
			.andExpect(jsonPath("$.progressionValue").value(33))
			.andExpect(jsonPath("$.progressionClass").value("LARGE_ARENA_TROT"))
			.andExpect(jsonPath("$.effectiveClass").value("ROUND_TROT"))
			.andExpect(jsonPath("$.progressionBaselineActualRideCount").value(10))
			.andExpect(jsonPath("$.specialApprovalProgressionCredit").value(5));

		assertThat(progressionStartedAt(memberId)).isEqualTo(startedAt);
		assertThat(progressionInputs(memberId)).isEqualTo(
			new ProgressionInputs("LARGE_ARENA_TROT", 26, 10, 5, "ROUND_TROT"));
		assertThat(auditRows(memberId)).containsExactly(
			new AuditRow(2, 10, 12, "m32-07-admin", "완료 집계 2회 누락 정정"));
	}

	@Test
	void zero_delta와_음수가_되는_보정과_미초기화_회원은_거부하고_감사하지_않는다() throws Exception {
		final long managedId = insertManagedMember("m32-07-invalid-member", 1);
		final long overflowId = insertManagedMember("m32-07-overflow-member", Integer.MAX_VALUE);
		final long uninitializedId = insertUninitializedMember("m32-07-uninitialized-member", 1);

		assertRejectedAdjustment(managedId, 0, 400, "MEMBER_INVALID_RIDE_COUNT_ADJUSTMENT");
		assertRejectedAdjustment(managedId, -2, 400, "MEMBER_INVALID_RIDE_COUNT_ADJUSTMENT");
		assertRejectedAdjustment(overflowId, 1, 400, "MEMBER_INVALID_RIDE_COUNT_ADJUSTMENT");
		assertRejectedAdjustment(uninitializedId, 1, 409, "MEMBER_PROGRESSION_NOT_INITIALIZED");

		assertThat(actualCount(managedId)).isOne();
		assertThat(actualCount(overflowId)).isEqualTo(Integer.MAX_VALUE);
		assertThat(actualCount(uninitializedId)).isOne();
		assertThat(auditRows(managedId)).isEmpty();
		assertThat(auditRows(overflowId)).isEmpty();
		assertThat(auditRows(uninitializedId)).isEmpty();
	}

	@Test
	void 유효한_음수_delta는_actual_count와_progression을_감소시키고_감사한다() throws Exception {
		final long memberId = insertManagedMember("m32-07-negative-member", 3);

		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"delta":-2,"reason":"중복 집계 2회 제거"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalRideCount").value(1))
			.andExpect(jsonPath("$.progressionValue").value(1))
			.andExpect(jsonPath("$.progressionClass").value("ROUND_BEGINNER"));

		assertThat(auditRows(memberId)).containsExactly(
			new AuditRow(-2, 3, 1, "m32-07-admin", "중복 집계 2회 제거"));
	}

	@Test
	void 회원은_보정할_수_없고_관리자_사유는_필수다() throws Exception {
		final long memberId = insertManagedMember("m32-07-role-member", 3);

		mockMvc.perform(post(endpoint(memberId))
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"delta":1,"reason":"권한 없음"}
					"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"delta":1,"reason":" "}
					"""))
			.andExpect(status().isBadRequest());

		assertThat(actualCount(memberId)).isEqualTo(3);
		assertThat(auditRows(memberId)).isEmpty();
	}

	private void assertRejectedAdjustment(
		long memberId,
		int delta,
		int expectedStatus,
		String expectedCode
	) throws Exception {
		mockMvc.perform(post(endpoint(memberId))
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"delta":%d,"reason":"잘못된 집계 정정"}
					""".formatted(delta)))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(expectedCode));
	}

	private long insertManagedMember(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, progression_management_started_at
			) VALUES (?, '관리 회원', '010-0000-0000', ?, CURRENT_TIMESTAMP(6))
			""", authSubject, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertManagedMemberWithProgressionInputs(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, progression_management_started_at,
				progression_baseline_class, progression_baseline_threshold,
				progression_baseline_actual_ride_count, special_approval_progression_credit,
				promotion_hold_class
			) VALUES (?, '관리 회원', '010-0000-0000', ?, CURRENT_TIMESTAMP(6),
				'LARGE_ARENA_TROT', 26, ?, 5, 'ROUND_TROT')
			""", authSubject, actualCount, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertUninitializedMember(String authSubject, int actualCount) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, general_ride_count)
			VALUES (?, '미초기화 회원', '010-0000-0000', ?)
			""", authSubject, actualCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private int actualCount(long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?",
			Integer.class,
			memberId);
	}

	private LocalDateTime progressionStartedAt(long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT progression_management_started_at FROM members WHERE id = ?",
			LocalDateTime.class,
			memberId);
	}

	private ProgressionInputs progressionInputs(long memberId) {
		return jdbcTemplate.queryForObject("""
			SELECT progression_baseline_class, progression_baseline_threshold,
				progression_baseline_actual_ride_count, special_approval_progression_credit,
				promotion_hold_class
			FROM members WHERE id = ?
			""", (resultSet, rowNumber) -> new ProgressionInputs(
			resultSet.getString(1),
			resultSet.getInt(2),
			resultSet.getInt(3),
			resultSet.getInt(4),
			resultSet.getString(5)), memberId);
	}

	private List<AuditRow> auditRows(long memberId) {
		return jdbcTemplate.query("""
			SELECT
				CAST(JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.rideCountDelta')) AS SIGNED),
				CAST(JSON_UNQUOTE(JSON_EXTRACT(from_state, '$.actualCompletedRideCount')) AS SIGNED),
				CAST(JSON_UNQUOTE(JSON_EXTRACT(to_state, '$.actualCompletedRideCount')) AS SIGNED),
				actor_auth_subject,
				reason
			FROM member_class_progression_audit_logs
			WHERE member_id = ? AND action = 'RIDE_COUNT_ADJUSTED'
			ORDER BY id
			""", (resultSet, rowNumber) -> new AuditRow(
			resultSet.getInt(1),
			resultSet.getInt(2),
			resultSet.getInt(3),
			resultSet.getString(4),
			resultSet.getString(5)), memberId);
	}

	private void clearTestData() {
		jdbcTemplate.update("""
			DELETE FROM member_class_progression_audit_logs
			WHERE member_id IN (
				SELECT id FROM members WHERE auth_subject LIKE 'm32-07-%'
			)
			""");
		jdbcTemplate.update("DELETE FROM members WHERE auth_subject LIKE 'm32-07-%'");
	}

	private String endpoint(long memberId) {
		return "/api/admin/members/" + memberId + "/class-progression/ride-count-adjustments";
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("m32-07-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt()
			.jwt(token -> token.subject("m32-07-member"))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private record ProgressionInputs(
		String baselineClass,
		int baselineThreshold,
		int baselineActualRideCount,
		int specialApprovalProgressionCredit,
		String promotionHoldClass
	) {
	}

	private record AuditRow(
		int delta,
		int beforeCount,
		int afterCount,
		String actor,
		String reason
	) {
	}
}
