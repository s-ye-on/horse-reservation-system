package com.horse.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

class ErrorResponseWriterTest {

	private final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter(new ObjectMapper());

	@Test
	void 이미_commit된_응답에는_오류_응답을_다시_작성하지_않는다() throws Exception {
		final MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(204);
		response.getWriter().write("already-written");
		response.flushBuffer();

		errorResponseWriter.write(response, ErrorResponse.from(ExceptionCode.COMMON_INTERNAL_ERROR, "/api/test"));

		assertThat(response.getStatus()).isEqualTo(204);
		assertThat(response.getContentAsString()).isEqualTo("already-written");
	}
}
