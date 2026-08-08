package com.horse.auth.presentation.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record WebCsrfTokenResponse(
	String headerName,
	String cookieName
) {
	public static WebCsrfTokenResponse from(CsrfToken csrfToken) {
		csrfToken.getToken();
		return new WebCsrfTokenResponse(csrfToken.getHeaderName(), "XSRF-TOKEN");
	}
}
