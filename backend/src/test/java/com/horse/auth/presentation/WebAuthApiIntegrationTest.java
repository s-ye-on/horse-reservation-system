package com.horse.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.domain.RefreshTokenSessionStatus;
import com.horse.auth.infrastructure.RefreshTokenSessionRepository;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WebAuthApiIntegrationTest {

	private static final String EMAIL = "web-auth@example.com";
	private static final String OTHER_EMAIL = "other-web-auth@example.com";
	private static final String PASSWORD = "web-auth-password-1234";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final String REFRESH_COOKIE = "HORSE_REFRESH_TOKEN";
	private static final String WEB_ORIGIN = "http://localhost:5173";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RefreshTokenSessionRepository refreshTokenSessionRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void 웹_인증_테스트_데이터를_초기화한다() {
		final List<Long> memberIds = jdbcTemplate.queryForList(
			"SELECT member_id FROM auth_accounts WHERE member_id IS NOT NULL",
			Long.class
		);
		jdbcTemplate.update("UPDATE refresh_token_sessions SET parent_session_id = NULL");
		jdbcTemplate.update("DELETE FROM refresh_token_sessions");
		jdbcTemplate.update("DELETE FROM auth_accounts");
		memberIds.forEach(memberId -> jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId));
	}

	@Test
	void CSRF를_초기화한_웹_로그인은_Access_Token과_HttpOnly_Refresh_Cookie만_반환한다() throws Exception {
		signup(EMAIL);
		final CsrfExchange csrf = initializeCsrf();

		final MvcResult result = webLogin(EMAIL, csrf);
		final JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		final Cookie refreshCookie = requiredCookie(result, REFRESH_COOKIE);

		assertThat(body.get("accessToken").asText()).isNotBlank();
		assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
		assertThat(body.get("accessTokenExpiresAt").asText()).endsWith("Z");
		assertThat(body.has("refreshToken")).isFalse();
		assertThat(body.has("refreshTokenExpiresAt")).isFalse();
		assertRefreshCookie(refreshCookie, 2_592_000);
		assertNoSessionCookie(result);
	}

	@Test
	void 웹_로그인_refresh_logout은_CSRF가_없으면_공통_403을_반환한다() throws Exception {
		signup(EMAIL);
		final CsrfExchange csrf = initializeCsrf();
		final Cookie refreshCookie = requiredCookie(webLogin(EMAIL, csrf), REFRESH_COOKIE);

		assertCommonCsrfForbidden(post("/api/auth/web/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginRequest(EMAIL)));
		assertCommonCsrfForbidden(post("/api/auth/web/refresh").cookie(refreshCookie));
		assertCommonCsrfForbidden(post("/api/auth/web/logout").cookie(refreshCookie));
	}

	@Test
	void 웹_refresh는_Cookie를_회전하고_ROTATED_재사용은_family를_폐기한다() throws Exception {
		signup(EMAIL);
		final CsrfExchange csrf = initializeCsrf();
		final Cookie firstCookie = requiredCookie(webLogin(EMAIL, csrf), REFRESH_COOKIE);

		final MvcResult refreshResult = webRefresh(firstCookie, csrf, 200);
		final Cookie successorCookie = requiredCookie(refreshResult, REFRESH_COOKIE);
		assertThat(successorCookie.getValue()).isNotEqualTo(firstCookie.getValue());
		assertThat(objectMapper.readTree(refreshResult.getResponse().getContentAsString()).has("refreshToken"))
			.isFalse();

		final MvcResult reuseResult = webRefresh(firstCookie, csrf, 401);
		assertThat(objectMapper.readTree(reuseResult.getResponse().getContentAsString()).get("code").asText())
			.isEqualTo("AUTH_INVALID_REFRESH_TOKEN");
		assertThat(requiredCookie(reuseResult, REFRESH_COOKIE).getMaxAge()).isZero();
		webRefresh(successorCookie, csrf, 401);
		assertThat(refreshTokenSessionRepository.findAll())
			.noneMatch(session -> session.getStatus() == RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 웹_refresh의_누락되거나_알_수_없는_Cookie는_삭제하고_공통_401을_반환한다() throws Exception {
		final CsrfExchange csrf = initializeCsrf();

		final MvcResult missingResult = mockMvc.perform(post("/api/auth/web/refresh")
			.cookie(csrf.cookie())
			.header(csrf.headerName(), csrf.token()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"))
			.andReturn();
		assertThat(requiredCookie(missingResult, REFRESH_COOKIE).getMaxAge()).isZero();

		final Cookie unknownCookie = new Cookie(REFRESH_COOKIE, "unknown-refresh-token");
		final MvcResult unknownResult = webRefresh(unknownCookie, csrf, 401);
		assertThat(requiredCookie(unknownResult, REFRESH_COOKIE).getMaxAge()).isZero();
	}

	@Test
	void 웹_재사용_폐기는_다른_로그인_family에_영향을_주지_않는다() throws Exception {
		signup(EMAIL);
		signup(OTHER_EMAIL);
		final CsrfExchange csrf = initializeCsrf();
		final Cookie compromisedCookie = requiredCookie(webLogin(EMAIL, csrf), REFRESH_COOKIE);
		final Cookie otherCookie = requiredCookie(webLogin(OTHER_EMAIL, csrf), REFRESH_COOKIE);
		final Cookie compromisedSuccessor = requiredCookie(
			webRefresh(compromisedCookie, csrf, 200),
			REFRESH_COOKIE
		);

		webRefresh(compromisedCookie, csrf, 401);
		webRefresh(compromisedSuccessor, csrf, 401);
		webRefresh(otherCookie, csrf, 200);

		assertThat(refreshTokenSessionRepository.findAll())
			.anyMatch(session -> session.getStatus() == RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 웹_logout은_Session을_폐기하고_invalid_Cookie도_삭제하며_204를_반환한다() throws Exception {
		signup(EMAIL);
		final CsrfExchange csrf = initializeCsrf();
		final Cookie refreshCookie = requiredCookie(webLogin(EMAIL, csrf), REFRESH_COOKIE);

		final MvcResult logoutResult = webLogout(refreshCookie, csrf);
		assertThat(requiredCookie(logoutResult, REFRESH_COOKIE).getMaxAge()).isZero();
		assertThat(refreshTokenSessionRepository.findAll()).singleElement()
			.extracting(session -> session.getStatus())
			.isEqualTo(RefreshTokenSessionStatus.REVOKED);
		webRefresh(refreshCookie, csrf, 401);
		assertThat(requiredCookie(webLogout(refreshCookie, csrf), REFRESH_COOKIE).getMaxAge()).isZero();

		final Cookie expiredCookie = requiredCookie(webLogin(EMAIL, csrf), REFRESH_COOKIE);
		jdbcTemplate.update(
			"UPDATE refresh_token_sessions SET expires_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND) "
				+ "WHERE status = 'ACTIVE'"
		);
		assertThat(requiredCookie(webLogout(expiredCookie, csrf), REFRESH_COOKIE).getMaxAge()).isZero();

		final Cookie invalidCookie = new Cookie(REFRESH_COOKIE, "unknown-refresh-token");
		final MvcResult invalidLogoutResult = webLogout(invalidCookie, csrf);
		assertThat(requiredCookie(invalidLogoutResult, REFRESH_COOKIE).getMaxAge()).isZero();
		final MvcResult missingLogoutResult = mockMvc.perform(post("/api/auth/web/logout")
			.cookie(csrf.cookie())
			.header(csrf.headerName(), csrf.token()))
			.andExpect(status().isNoContent())
			.andReturn();
		assertThat(requiredCookie(missingLogoutResult, REFRESH_COOKIE).getMaxAge()).isZero();
	}

	@Test
	void 개발_CORS는_localhost_credential_preflight만_허용한다() throws Exception {
		mockMvc.perform(options("/api/auth/web/refresh")
			.header(HttpHeaders.ORIGIN, WEB_ORIGIN)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN, Content-Type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				org.hamcrest.Matchers.containsString("X-XSRF-TOKEN")));

		mockMvc.perform(options("/api/auth/web/refresh")
			.header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void 웹_인증_prefix의_정의되지_않은_Endpoint는_공통_401로_거부한다() throws Exception {
		mockMvc.perform(get("/api/auth/web/internal"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
	}

	private CsrfExchange initializeCsrf() throws Exception {
		final MvcResult result = mockMvc.perform(get("/api/auth/web/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
			.andExpect(jsonPath("$.cookieName").value(CSRF_COOKIE))
			.andReturn();
		final Cookie cookie = requiredCookie(result, CSRF_COOKIE);
		assertThat(cookie.isHttpOnly()).isFalse();
		assertNoSessionCookie(result);
		return new CsrfExchange("X-XSRF-TOKEN", cookie.getValue(), cookie);
	}

	private MvcResult webLogin(String email, CsrfExchange csrf) throws Exception {
		return mockMvc.perform(post("/api/auth/web/login")
			.cookie(csrf.cookie())
			.header(csrf.headerName(), csrf.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginRequest(email)))
			.andExpect(status().isOk())
			.andReturn();
	}

	private MvcResult webRefresh(Cookie refreshCookie, CsrfExchange csrf, int expectedStatus) throws Exception {
		final MvcResult result = mockMvc.perform(post("/api/auth/web/refresh")
			.cookie(csrf.cookie(), refreshCookie)
			.header(csrf.headerName(), csrf.token()))
			.andExpect(status().is(expectedStatus))
			.andReturn();
		assertNoSessionCookie(result);
		return result;
	}

	private MvcResult webLogout(Cookie refreshCookie, CsrfExchange csrf) throws Exception {
		final MvcResult result = mockMvc.perform(post("/api/auth/web/logout")
			.cookie(csrf.cookie(), refreshCookie)
			.header(csrf.headerName(), csrf.token()))
			.andExpect(status().isNoContent())
			.andReturn();
		assertNoSessionCookie(result);
		return result;
	}

	private void signup(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(
				new SignupRequest(email, PASSWORD, "웹 인증 회원", "010-1234-5678")
			)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	private String loginRequest(String email) throws Exception {
		return objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD));
	}

	private Cookie requiredCookie(MvcResult result, String name) {
		final Cookie cookie = result.getResponse().getCookie(name);
		assertThat(cookie).as("response cookie %s", name).isNotNull();
		return cookie;
	}

	private void assertRefreshCookie(Cookie cookie, int expectedMaxAge) {
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isFalse();
		assertThat(cookie.getPath()).isEqualTo("/api/auth/web");
		assertThat(cookie.getMaxAge()).isEqualTo(expectedMaxAge);
		assertThat(cookie.getDomain()).isNull();
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
	}

	private void assertNoSessionCookie(MvcResult result) {
		assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
	}

	private void assertCommonCsrfForbidden(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
	) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"))
			.andExpect(jsonPath("$.status").value(403));
	}

	private record CsrfExchange(String headerName, String token, Cookie cookie) {
	}

	private record SignupRequest(String email, String password, String name, String phone) {
	}

	private record LoginRequest(String email, String password) {
	}
}
