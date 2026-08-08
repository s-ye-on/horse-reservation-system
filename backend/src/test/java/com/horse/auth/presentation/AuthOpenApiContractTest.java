package com.horse.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.horse.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class AuthOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void 인증_Endpoint와_required_nullable_RFC3339_오류_계약을_공개한다() throws Exception {
		final JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());

		assertThat(document.at("/paths/~1api~1auth~1signup/post/operationId").asText()).isEqualTo("signup");
		assertThat(document.at("/paths/~1api~1auth~1login/post/operationId").asText()).isEqualTo("login");
		assertThat(document.at("/paths/~1api~1auth~1refresh/post/operationId").asText())
			.isEqualTo("refreshAccessToken");
		assertThat(document.at("/paths/~1api~1auth~1logout/post/operationId").asText()).isEqualTo("logout");
		assertThat(document.at("/paths/~1api~1auth~1me/get/operationId").asText())
			.isEqualTo("getCurrentAuthAccount");
		assertThat(document.at("/paths/~1api~1auth~1web~1csrf/get/operationId").asText())
			.isEqualTo("initializeWebCsrfToken");
		assertThat(document.at("/paths/~1api~1auth~1web~1csrf/get/parameters").isMissingNode()).isTrue();
		assertThat(document.at("/paths/~1api~1auth~1web~1login/post/operationId").asText())
			.isEqualTo("webLogin");
		assertThat(document.at("/paths/~1api~1auth~1web~1refresh/post/operationId").asText())
			.isEqualTo("refreshWebAccessToken");
		assertThat(document.at("/paths/~1api~1auth~1web~1logout/post/operationId").asText())
			.isEqualTo("webLogout");
		assertThat(document.at("/paths/~1api~1auth~1me/get/security/0/bearerAuth").isArray()).isTrue();
		assertThat(document.at("/paths/~1api~1auth~1login/post/security").isMissingNode()).isTrue();

		assertRequired(document, "AuthSignupRequest", "email", "password", "name", "phone");
		assertRequired(document, "AuthLoginRequest", "email", "password");
		assertRequired(document, "AuthRefreshRequest", "refreshToken");
		assertRequired(document, "AuthLogoutRequest", "refreshToken");
		assertRequired(
			document,
			"AuthTokenResponse",
			"accessToken",
			"refreshToken",
			"tokenType",
			"accessTokenExpiresAt",
			"refreshTokenExpiresAt"
		);
		assertRequired(document, "WebAuthTokenResponse", "accessToken", "tokenType", "accessTokenExpiresAt");
		assertRequired(document, "WebCsrfTokenResponse", "headerName", "cookieName");
		assertThat(document.at("/components/schemas/WebAuthTokenResponse/properties/refreshToken").isMissingNode())
			.isTrue();
		assertThat(document.at("/paths/~1api~1auth~1web~1refresh/post/requestBody").isMissingNode()).isTrue();
		assertThat(document.at("/paths/~1api~1auth~1web~1logout/post/requestBody").isMissingNode()).isTrue();
		assertRequiredHeader(document, "/paths/~1api~1auth~1web~1login/post/parameters", "X-XSRF-TOKEN");
		assertRequiredHeader(document, "/paths/~1api~1auth~1web~1refresh/post/parameters", "X-XSRF-TOKEN");
		assertRequiredHeader(document, "/paths/~1api~1auth~1web~1logout/post/parameters", "X-XSRF-TOKEN");
		assertRequired(document, "AuthAccountResponse", "subject", "memberId", "email", "role", "status");
		assertThat(document.at("/components/schemas/AuthSignupRequest/properties/role").isMissingNode()).isTrue();
		assertThat(document.at("/components/schemas/AuthSignupRequest/properties/password/writeOnly").asBoolean())
			.isTrue();
		assertThat(document.at("/components/schemas/AuthAccountResponse/properties/memberId/type")
			.valueStream().map(JsonNode::asText).toList())
			.containsExactly("integer", "null");
		assertThat(document.at(
			"/components/schemas/AuthTokenResponse/properties/accessTokenExpiresAt/format").asText())
			.isEqualTo("date-time");
		assertThat(document.at(
			"/components/schemas/AuthTokenResponse/properties/refreshTokenExpiresAt/format").asText())
			.isEqualTo("date-time");
		assertThat(document.at(
			"/paths/~1api~1auth~1login/post/responses/401/content/application~1json/schema/$ref").asText())
			.isEqualTo("#/components/schemas/ErrorResponse");
		assertThat(document.at(
			"/paths/~1api~1auth~1signup/post/responses/409/content/application~1json/schema/$ref").asText())
			.isEqualTo("#/components/schemas/ErrorResponse");
	}

	private void assertRequired(JsonNode document, String schemaName, String... fields) {
		final List<String> required = document.at("/components/schemas/" + schemaName + "/required")
			.valueStream()
			.map(JsonNode::asText)
			.toList();
		assertThat(required).containsExactlyInAnyOrder(fields);
	}

	private void assertRequiredHeader(JsonNode document, String path, String name) {
		assertThat(document.at(path).valueStream())
			.anySatisfy(parameter -> {
				assertThat(parameter.get("name").asText()).isEqualTo(name);
				assertThat(parameter.get("in").asText()).isEqualTo("header");
				assertThat(parameter.get("required").asBoolean()).isTrue();
			});
	}
}
