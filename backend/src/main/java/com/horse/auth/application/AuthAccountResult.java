package com.horse.auth.application;

import com.horse.auth.UserRole;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.domain.AuthAccountStatus;

public record AuthAccountResult(
	String subject,
	Long memberId,
	String email,
	UserRole role,
	AuthAccountStatus status
) {

	public static AuthAccountResult from(AuthAccount account) {
		return new AuthAccountResult(
			account.getAuthSubject(),
			account.getMemberId(),
			account.getNormalizedEmail(),
			account.getRole(),
			account.getStatus()
		);
	}
}
