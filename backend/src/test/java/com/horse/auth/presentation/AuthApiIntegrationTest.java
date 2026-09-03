package com.horse.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.application.InitialAdminBootstrapService;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.auth.infrastructure.RefreshTokenSessionRepository;
import com.horse.auth.support.AuthIntegrationTestDataCleaner;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

	private static final String EMAIL = "member@example.com";
	private static final String PASSWORD = "test-password-1234";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	AuthAccountRepository authAccountRepository;

	@Autowired
	RefreshTokenSessionRepository refreshTokenSessionRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	InitialAdminBootstrapService bootstrapService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void 인증_테스트_데이터를_초기화한다() {
		AuthIntegrationTestDataCleaner.clean(jdbcTemplate);
	}

	@Test
	void 회원가입은_이메일을_정규화하고_MEMBER_계정을_생성한다() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest("  MEMBER@Example.COM ")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.subject").isString())
			.andExpect(jsonPath("$.memberId").isNumber())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		final AuthAccount account = authAccountRepository.findByNormalizedEmail(EMAIL).orElseThrow();
		assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
		assertThat(account.getMemberId()).isNotNull();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?",
			Integer.class,
			account.getMemberId()
		)).isZero();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT dressage_approved OR jumping_approved FROM members WHERE id = ?",
			Boolean.class,
			account.getMemberId()
		)).isFalse();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupons WHERE member_id = ?",
			Long.class,
			account.getMemberId()
		)).isZero();
	}

	@Test
	void 회원가입은_8자_비밀번호를_허용하고_7자를_거부한다() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new SignupRequest("eight@example.com", "12345678", "8자 회원", "010-1111-2222")
				)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
					new SignupRequest("seven@example.com", "1234567", "7자 회원", "010-2222-3333")
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void 공개_회원가입_body의_role은_ADMIN을_만들지_못한다() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupRequestWithRole(EMAIL)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("MEMBER"));

		assertThat(authAccountRepository.findByNormalizedEmail(EMAIL).orElseThrow().getRole().name())
			.isEqualTo("MEMBER");
	}

	@Test
	void 정규화_이메일이_같으면_409_공통_오류를_반환한다() throws Exception {
		signup(EMAIL);

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(" MEMBER@EXAMPLE.COM ")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_EXISTS"))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void 로그인은_기존_claim_계약의_Access_Token과_회전용_Refresh_Token을_발급한다() throws Exception {
		signup(EMAIL);

		final JsonNode tokens = login(EMAIL, PASSWORD);
		final Jwt accessToken = jwtDecoder.decode(tokens.get("accessToken").asText());
		final AuthAccount account = authAccountRepository.findByNormalizedEmail(EMAIL).orElseThrow();

		assertThat(accessToken.getSubject()).isEqualTo(account.getAuthSubject());
		assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("MEMBER");
		assertThat(accessToken.getClaimAsString("iss")).isEqualTo("horse-api");
		assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
		assertThat(tokens.get("accessTokenExpiresAt").asText()).endsWith("Z");
		assertThat(tokens.get("refreshTokenExpiresAt").asText()).contains("+09:00");
		assertThat(refreshTokenSessionRepository.findAll()).singleElement()
			.extracting(session -> session.getTokenHash())
			.isNotEqualTo(tokens.get("refreshToken").asText());
	}

	@Test
	void 존재하지_않는_이메일과_틀린_비밀번호는_같은_401을_반환한다() throws Exception {
		signup(EMAIL);

		assertInvalidCredentials("missing@example.com", PASSWORD);
		assertInvalidCredentials(EMAIL, "wrong-password-value");
		assertInvalidCredentials(EMAIL, "가".repeat(73));
	}

	@Test
	void 비활성_계정은_로그인할_수_없다() throws Exception {
		signup(EMAIL);
		final AuthAccount account = authAccountRepository.findByNormalizedEmail(EMAIL).orElseThrow();
		account.deactivate();
		authAccountRepository.saveAndFlush(account);

		assertInvalidCredentials(EMAIL, PASSWORD);
	}

	@Test
	void 차단과_탈퇴_계정도_로그인할_수_없다() throws Exception {
		final String blockedEmail = "blocked@example.com";
		final String withdrawnEmail = "withdrawn@example.com";
		signup(blockedEmail);
		signup(withdrawnEmail);

		final AuthAccount blocked = authAccountRepository.findByNormalizedEmail(blockedEmail).orElseThrow();
		blocked.block();
		authAccountRepository.saveAndFlush(blocked);
		final AuthAccount withdrawn = authAccountRepository.findByNormalizedEmail(withdrawnEmail).orElseThrow();
		withdrawn.withdraw();
		authAccountRepository.saveAndFlush(withdrawn);

		assertInvalidCredentials(blockedEmail, PASSWORD);
		assertInvalidCredentials(withdrawnEmail, PASSWORD);
	}

	@Test
	void me는_발급된_Access_Token의_최소_계정_정보만_반환한다() throws Exception {
		signup(EMAIL);
		final JsonNode tokens = login(EMAIL, PASSWORD);

		mockMvc.perform(get("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subject").value(jwtDecoder.decode(tokens.get("accessToken").asText()).getSubject()))
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	@Test
	void refresh는_토큰을_회전하고_이전_토큰_재사용을_거부한다() throws Exception {
		signup(EMAIL);
		final String firstRefreshToken = login(EMAIL, PASSWORD).get("refreshToken").asText();

		final JsonNode rotated = refresh(firstRefreshToken, 200);
		final String successorRefreshToken = rotated.get("refreshToken").asText();
		assertThat(successorRefreshToken).isNotEqualTo(firstRefreshToken);
		refresh(firstRefreshToken, 401);
		refresh(firstRefreshToken, 401);
		refresh(successorRefreshToken, 401);
		assertThat(refreshTokenSessionRepository.count()).isEqualTo(2L);
	}

	@Test
	void logout은_Refresh_Session을_폐기하고_동일_요청을_안전하게_처리한다() throws Exception {
		signup(EMAIL);
		final String refreshToken = login(EMAIL, PASSWORD).get("refreshToken").asText();

		logout(refreshToken, 200);
		logout(refreshToken, 200);
		refresh(refreshToken, 401);
	}

	@Test
	void 공개_인증_API는_인증이_필요없지만_me는_인증이_필요하다() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest("missing@example.com", PASSWORD)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 발급된_MEMBER와_ADMIN_Access_Token은_기존_권한_경계를_유지한다() throws Exception {
		signup(EMAIL);
		final String memberAccessToken = login(EMAIL, PASSWORD).get("accessToken").asText();
		bootstrapService.bootstrap("admin@example.com", PASSWORD);
		final String adminAccessToken = login("admin@example.com", PASSWORD).get("accessToken").asText();

		mockMvc.perform(get("/api/me/eligible-classes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberAccessToken))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/members")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + memberAccessToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("COMMON_FORBIDDEN"));
		mockMvc.perform(get("/api/admin/members")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
			.andExpect(status().isOk());
	}

	@Test
	void 만료되거나_위조된_Access_Token은_공통_401로_거부한다() throws Exception {
		final Instant issuedAt = Instant.now().minusSeconds(120);
		final JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
			.issuer("horse-api")
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plusSeconds(60))
			.subject("expired-subject")
			.claim("roles", List.of("MEMBER"))
			.build();
		final String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
			JwsHeader.with(MacAlgorithm.HS256).build(),
			expiredClaims
		)).getTokenValue();

		assertUnauthorizedAccessToken(expiredToken);
		assertUnauthorizedAccessToken("forged.access.token");
	}

	@Test
	void 만료되거나_위조된_Refresh_Token은_공통_401로_거부한다() throws Exception {
		signup(EMAIL);
		final String refreshToken = login(EMAIL, PASSWORD).get("refreshToken").asText();
		final Long sessionId = refreshTokenSessionRepository.findAll().getFirst().getId();
		jdbcTemplate.update(
			"UPDATE refresh_token_sessions SET expires_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND) WHERE id = ?",
			sessionId
		);

		refresh(refreshToken, 401);
		refresh("forged-refresh-token", 401);
	}

	private void signup(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email)))
			.andExpect(status().isCreated());
	}

	private JsonNode login(String email, String password) throws Exception {
		final MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, password)))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode refresh(String token, int expectedStatus) throws Exception {
		final MvcResult result = mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new TokenRequest(token))))
			.andExpect(status().is(expectedStatus))
			.andReturn();
		final String responseBody = result.getResponse().getContentAsString();
		final JsonNode response = objectMapper.readTree(responseBody);
		if (expectedStatus == 401) {
			assertThat(response.get("code").asText()).isEqualTo("AUTH_INVALID_REFRESH_TOKEN");
			assertThat(response.get("details").isEmpty()).isTrue();
			assertThat(responseBody)
				.doesNotContain(token)
				.doesNotContain("refreshToken")
				.doesNotContain("tokenHash")
				.doesNotContain("sessionId");
		}
		return response;
	}

	private void logout(String token, int expectedStatus) throws Exception {
		mockMvc.perform(post("/api/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new TokenRequest(token))))
			.andExpect(status().is(expectedStatus));
	}

	private void assertInvalidCredentials(String email, String password) throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest(email, password)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	private void assertUnauthorizedAccessToken(String accessToken) throws Exception {
		mockMvc.perform(get("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
	}

	private String signupRequest(String email) throws Exception {
		return objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, "테스트 회원", "010-1234-5678"));
	}

	private String signupRequestWithRole(String email) throws Exception {
		return """
			{"email":"%s","password":"%s","name":"테스트 회원","phone":"010-1234-5678","role":"ADMIN"}
			""".formatted(email, PASSWORD);
	}

	private String loginRequest(String email, String password) throws Exception {
		return objectMapper.writeValueAsString(new LoginRequest(email, password));
	}

	private record SignupRequest(String email, String password, String name, String phone) {
	}

	private record LoginRequest(String email, String password) {
	}

	private record TokenRequest(String refreshToken) {
	}
}
