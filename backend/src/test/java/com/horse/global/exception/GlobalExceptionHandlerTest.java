package com.horse.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import com.horse.members.domain.exception.MemberException;

import tools.jackson.databind.ObjectMapper;

class GlobalExceptionHandlerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(
		new ErrorResponseWriter(objectMapper)
	);

	@Test
	void 업무_예외를_오류_응답으로_변환한다() throws Exception {
		final MemberException exception = new MemberException(ExceptionCode.MEMBER_INVALID_NAME);
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
		final MockHttpServletResponse servletResponse = new MockHttpServletResponse();

		exceptionHandler.handleBusinessException(exception, request, servletResponse);
		final ErrorResponse response = objectMapper.readValue(servletResponse.getContentAsByteArray(), ErrorResponse.class);

		assertThat(servletResponse.getStatus()).isEqualTo(ExceptionCode.MEMBER_INVALID_NAME.status().value());
		assertThat(response).satisfies(body -> {
			assertThat(body.code()).isEqualTo(ExceptionCode.MEMBER_INVALID_NAME.code());
			assertThat(body.message()).isEqualTo(ExceptionCode.MEMBER_INVALID_NAME.message());
			assertThat(body.status()).isEqualTo(400);
			assertThat(body.timestamp()).isNotNull();
			assertThat(body.path()).isEqualTo("/api/me");
			assertThat(body.fieldErrors()).isEmpty();
			assertThat(body.details()).isEmpty();
		});
	}

	@Test
	void 바인딩_예외를_검증_오류_응답으로_변환한다() throws Exception {
		final BindException exception = new BindException(new Object(), "request");
		exception.addError(new FieldError(
			"request",
			"name",
			"노출하면 안 되는 값",
			false,
			null,
			null,
			"이름은 필수입니다."
		));
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/members");
		final MockHttpServletResponse servletResponse = new MockHttpServletResponse();

		exceptionHandler.handleBindException(exception, request, servletResponse);
		final ErrorResponse response = objectMapper.readValue(servletResponse.getContentAsByteArray(), ErrorResponse.class);

		assertThat(servletResponse.getStatus()).isEqualTo(ExceptionCode.COMMON_INVALID_REQUEST.status().value());
		assertThat(response).satisfies(body -> {
			assertThat(body.code()).isEqualTo(ExceptionCode.COMMON_INVALID_REQUEST.code());
			assertThat(body.message()).isEqualTo(ExceptionCode.COMMON_INVALID_REQUEST.message());
			assertThat(body.status()).isEqualTo(400);
			assertThat(body.timestamp()).isNotNull();
			assertThat(body.path()).isEqualTo("/api/admin/members");
			assertThat(body.fieldErrors()).containsExactly(
				new ErrorResponse.FieldError("name", "이름은 필수입니다.")
			);
			assertThat(body.details()).isEmpty();
			assertThat(body.toString()).doesNotContain("노출하면 안 되는 값");
		});
	}

}
