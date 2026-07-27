package com.horse.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

class GlobalExceptionHandlerApiTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void 요청_본문_검증_오류를_통합_오류_응답으로_반환한다() throws Exception {
		mockMvc.perform(post("/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"노출하면 안 되는 값\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.timestamp").isNotEmpty())
			.andExpect(jsonPath("$.path").value("/validation"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.fieldErrors[0].message").value("이름은 3자 이하여야 합니다."))
			.andExpect(jsonPath("$.details").isMap())
			.andExpect(content().string(not(containsString("노출하면 안 되는 값"))));
	}

	@RestController
	private static class ValidationController {

		@PostMapping("/validation")
		void validate(@Valid @RequestBody ValidationRequest request) {
		}
	}

	private record ValidationRequest(
		@Size(max = 3, message = "이름은 3자 이하여야 합니다.")
		String name
	) {
	}
}
