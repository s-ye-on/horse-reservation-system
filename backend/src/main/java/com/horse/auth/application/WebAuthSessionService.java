package com.horse.auth.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.horse.auth.domain.exception.AuthException;
import com.horse.global.exception.ExceptionCode;

@Service
public class WebAuthSessionService {

	private final AuthSessionService authSessionService;

	public WebAuthSessionService(AuthSessionService authSessionService) {
		this.authSessionService = authSessionService;
	}

	public AuthTokenResult login(String email, String password) {
		return authSessionService.login(email, password);
	}

	public AuthTokenResult refresh(Optional<String> refreshToken) {
		return authSessionService.refresh(refreshToken
			.orElseThrow(() -> new AuthException(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN)));
	}

	public void logout(Optional<String> refreshToken) {
		refreshToken.ifPresent(this::logoutIfKnown);
	}

	private void logoutIfKnown(String refreshToken) {
		try {
			authSessionService.logout(refreshToken);
		}
		catch (AuthException ignored) {
			// A web logout converges to a logged-out browser even when its cookie is stale.
		}
	}
}
