package com.horse.auth;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.web-auth")
public record WebAuthProperties(
	RefreshCookie refreshCookie,
	List<String> allowedOrigins
) {
	public WebAuthProperties {
		allowedOrigins = List.copyOf(allowedOrigins);
		if (allowedOrigins.contains("*")) {
			throw new IllegalStateException("웹 인증 credential CORS에는 wildcard Origin을 사용할 수 없습니다.");
		}
	}

	public record RefreshCookie(
		String name,
		boolean secure
	) {
	}
}
