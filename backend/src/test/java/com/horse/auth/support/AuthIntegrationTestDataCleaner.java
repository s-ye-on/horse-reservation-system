package com.horse.auth.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

public final class AuthIntegrationTestDataCleaner {

	private AuthIntegrationTestDataCleaner() {
	}

	public static void clean(JdbcTemplate jdbcTemplate) {
		final List<Long> memberIds = jdbcTemplate.queryForList(
			"SELECT member_id FROM auth_accounts WHERE member_id IS NOT NULL",
			Long.class
		);
		jdbcTemplate.update("UPDATE refresh_token_sessions SET parent_session_id = NULL");
		jdbcTemplate.update("DELETE FROM refresh_token_sessions");
		memberIds.forEach(memberId -> jdbcTemplate.update(
			"DELETE FROM member_class_progression_audit_logs WHERE member_id = ?",
			memberId));
		jdbcTemplate.update("DELETE FROM auth_accounts");
		memberIds.forEach(memberId -> jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId));
	}
}
