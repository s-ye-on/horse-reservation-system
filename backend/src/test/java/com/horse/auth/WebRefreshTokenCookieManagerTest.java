package com.horse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class WebRefreshTokenCookieManagerTest {

	@Test
	void credential_CORS의_wildcard_Origin은_설정할_수_없다() {
		assertThatThrownBy(() -> new WebAuthProperties(
			new WebAuthProperties.RefreshCookie("HORSE_REFRESH_TOKEN", false),
			List.of("*")
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("wildcard Origin");
	}

	@Test
	void 운영_Refresh_Cookie는_Secure이며_발급과_삭제_속성이_같다() {
		final AuthTokenProperties tokenProperties = new AuthTokenProperties(
			"horse-api",
			Duration.ofMinutes(15),
			Duration.ofDays(30)
		);
		final WebAuthProperties webAuthProperties = new WebAuthProperties(
			new WebAuthProperties.RefreshCookie("HORSE_REFRESH_TOKEN", true),
			List.of("https://web.example.test")
		);
		final WebRefreshTokenCookieManager manager = new WebRefreshTokenCookieManager(
			tokenProperties,
			webAuthProperties
		);
		final MockHttpServletResponse issueResponse = new MockHttpServletResponse();
		final MockHttpServletResponse clearResponse = new MockHttpServletResponse();

		manager.write(issueResponse, "test-refresh-token");
		manager.clear(clearResponse);

		final Cookie issued = issueResponse.getCookie("HORSE_REFRESH_TOKEN");
		final Cookie cleared = clearResponse.getCookie("HORSE_REFRESH_TOKEN");
		assertThat(issued).isNotNull();
		assertThat(cleared).isNotNull();
		assertThat(issued.getSecure()).isTrue();
		assertThat(issued.isHttpOnly()).isTrue();
		assertThat(issued.getMaxAge()).isEqualTo(2_592_000);
		assertThat(cleared.getMaxAge()).isZero();
		assertThat(cleared.getName()).isEqualTo(issued.getName());
		assertThat(cleared.getPath()).isEqualTo(issued.getPath()).isEqualTo("/api/auth/web");
		assertThat(cleared.getSecure()).isEqualTo(issued.getSecure());
		assertThat(cleared.getAttribute("SameSite"))
			.isEqualTo(issued.getAttribute("SameSite"))
			.isEqualTo("Lax");
	}
}
