package com.horse.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Import({ TestcontainersConfiguration.class, ErrorResponseContractApiTest.ErrorContractController.class })
@SpringBootTest
@AutoConfigureMockMvc
class ErrorResponseContractApiTest {

	private static final String ENDPOINT = "/api/error-contract";
	private static final String JWT_SECRET = "local-development-jwt-secret-change-me-32-bytes";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 잘못된_JSON과_Bean_Validation은_400_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(post(ENDPOINT + "/requests")
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":")), 400, "COMMON_INVALID_REQUEST", ENDPOINT + "/requests")
			.andExpect(jsonPath("$.fieldErrors").isEmpty());

		assertErrorResponse(mockMvc.perform(post(ENDPOINT + "/requests")
				.with(memberJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}")), 400, "COMMON_INVALID_REQUEST", ENDPOINT + "/requests")
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("이름은 필수입니다."));
	}

	@Test
	void 잘못된_path_variable과_query_parameter는_필드_오류_구조의_400을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/numbers/not-a-number").with(memberJwt())),
			400, "COMMON_INVALID_REQUEST", ENDPOINT + "/numbers/not-a-number")
			.andExpect(jsonPath("$.fieldErrors[0].field").value("id"));

		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/types").param("type", "UNKNOWN").with(memberJwt())),
			400, "COMMON_INVALID_REQUEST", ENDPOINT + "/types")
			.andExpect(jsonPath("$.fieldErrors[0].field").value("type"));

		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/types").with(memberJwt())),
			400, "COMMON_INVALID_REQUEST", ENDPOINT + "/types")
			.andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
	}

	@Test
	void 미인증과_유효하지_않은_JWT는_401_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/member")),
			401, "COMMON_UNAUTHORIZED", ENDPOINT + "/member");
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/member")
				.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")),
			401, "COMMON_UNAUTHORIZED", ENDPOINT + "/member");
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/member")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + signedJwt(Instant.now().minusSeconds(120), JWT_SECRET))),
			401, "COMMON_UNAUTHORIZED", ENDPOINT + "/member");
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/member")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + signedJwt(
					Instant.now().plusSeconds(120), "forged-jwt-secret-change-me-32-bytes"
				))), 401, "COMMON_UNAUTHORIZED", ENDPOINT + "/member");
	}

	@Test
	void 회원의_관리자_API_요청은_403_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get("/api/admin/error-contract").with(memberJwt())),
			403, "COMMON_FORBIDDEN", "/api/admin/error-contract");
	}

	@Test
	void 존재하지_않는_API는_정적_리소스_예외_경로를_거쳐_404_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/not-found").with(memberJwt())),
			404, "COMMON_NOT_FOUND", ENDPOINT + "/not-found")
			.andExpect(result -> assertThat(result.getResolvedException())
				.isInstanceOf(NoResourceFoundException.class));
	}

	@Test
	void 존재하는_URI의_잘못된_method는_405_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(put(ENDPOINT + "/member").with(memberJwt())),
			405, "COMMON_METHOD_NOT_ALLOWED", ENDPOINT + "/member");
	}

	@Test
	void 기존_도메인_충돌_예외는_details를_보존한_409_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/conflict").with(memberJwt())),
			409, "RESERVATION_INVALID_STATUS", ENDPOINT + "/conflict")
			.andExpect(jsonPath("$.details.reservationId").value(1));
	}

	@Test
	void 예상하지_않은_예외는_내부_정보_없이_500_공통_응답을_반환한다() throws Exception {
		assertErrorResponse(mockMvc.perform(get(ENDPOINT + "/unexpected").with(memberJwt())),
			500, "COMMON_INTERNAL_ERROR", ENDPOINT + "/unexpected")
			.andExpect(content().string(not(containsString("database connection secret"))));
	}

	private ResultActions assertErrorResponse(ResultActions result, int expectedStatus, String code, String path)
		throws Exception {
		return result
			.andExpect(status().is(expectedStatus))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").isNotEmpty())
			.andExpect(jsonPath("$.status").value(expectedStatus))
			.andExpect(jsonPath("$.timestamp").isNotEmpty())
			.andExpect(jsonPath("$.path").value(path))
			.andExpect(jsonPath("$.fieldErrors").isArray())
			.andExpect(jsonPath("$.details").isMap());
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private String signedJwt(Instant expiresAt, String secret) throws Exception {
		final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		final String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
			.getBytes(StandardCharsets.UTF_8));
		final String claims = encoder.encodeToString(("{\"sub\":\"member\",\"exp\":"
			+ expiresAt.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
		final Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		final String signature = encoder.encodeToString(mac.doFinal((header + "." + claims)
			.getBytes(StandardCharsets.UTF_8)));
		return header + "." + claims + "." + signature;
	}

	@RestController
	@RequestMapping(ENDPOINT)
	static class ErrorContractController {

		@PostMapping("/requests")
		void request(@Valid @RequestBody ErrorContractRequest request) {
		}

		@GetMapping("/member")
		void member() {
		}

		@GetMapping("/numbers/{id}")
		void number(@PathVariable Long id) {
		}

		@GetMapping("/types")
		void type(@RequestParam ContractType type) {
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ContractConflictException();
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database connection secret");
		}
	}

	private record ErrorContractRequest(
		@NotBlank(message = "이름은 필수입니다.")
		String name
	) {
	}

	private enum ContractType {
		ROUND
	}

	private static class ContractConflictException extends BusinessException {

		ContractConflictException() {
			super(ExceptionCode.RESERVATION_INVALID_STATUS, Map.of("reservationId", 1L));
		}
	}
}
