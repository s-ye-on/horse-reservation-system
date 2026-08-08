package com.horse.auth;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WebRefreshTokenCookieManager {
	static final String PATH = "/api/auth/web";
	static final String SAME_SITE = "Lax";

	private final AuthTokenProperties tokenProperties;
	private final WebAuthProperties webAuthProperties;

	public WebRefreshTokenCookieManager(
		AuthTokenProperties tokenProperties,
		WebAuthProperties webAuthProperties
	) {
		this.tokenProperties = tokenProperties;
		this.webAuthProperties = webAuthProperties;
	}

	public Optional<String> read(HttpServletRequest request) {
		final Cookie cookie = WebUtils.getCookie(request, cookieName());
		if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(cookie.getValue());
	}

	public void write(HttpServletResponse response, String refreshToken) {
		add(response, refreshToken, tokenProperties.refreshTokenTtl());
	}

	public void clear(HttpServletResponse response) {
		add(response, "", Duration.ZERO);
	}

	private void add(HttpServletResponse response, String value, Duration maxAge) {
		final ResponseCookie cookie = ResponseCookie.from(cookieName(), value)
			.httpOnly(true)
			.secure(webAuthProperties.refreshCookie().secure())
			.sameSite(SAME_SITE)
			.path(PATH)
			.maxAge(maxAge)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private String cookieName() {
		return webAuthProperties.refreshCookie().name();
	}
}
