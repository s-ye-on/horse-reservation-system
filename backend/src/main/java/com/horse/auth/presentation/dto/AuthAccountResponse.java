package com.horse.auth.presentation.dto;

import com.horse.auth.application.AuthAccountResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthAccountResponse(
	String subject,
	@Schema(nullable = true) Long memberId,
	String email,
	String role,
	String status
) {

	public static AuthAccountResponse from(AuthAccountResult result) {
		return new AuthAccountResponse(
			result.subject(),
			result.memberId(),
			result.email(),
			result.role().name(),
			result.status().name()
		);
	}
}
