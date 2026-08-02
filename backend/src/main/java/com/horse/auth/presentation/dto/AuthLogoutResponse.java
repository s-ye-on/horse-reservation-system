package com.horse.auth.presentation.dto;

public record AuthLogoutResponse(
	boolean loggedOut
) {

	public static AuthLogoutResponse success() {
		return new AuthLogoutResponse(true);
	}
}
