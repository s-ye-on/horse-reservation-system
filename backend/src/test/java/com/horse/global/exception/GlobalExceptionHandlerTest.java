package com.horse.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.horse.members.domain.exception.MemberException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	@Test
	void 업무_예외를_오류_응답으로_변환한다() {
		MemberException exception = new MemberException(ExceptionCode.MEMBER_INVALID_NAME);

		var response = exceptionHandler.handleBusinessException(exception);

		assertThat(response.getStatusCode()).isEqualTo(ExceptionCode.MEMBER_INVALID_NAME.status());
		assertThat(response.getBody()).isEqualTo(new ErrorResponse(
			ExceptionCode.MEMBER_INVALID_NAME.code(),
			ExceptionCode.MEMBER_INVALID_NAME.message()
		));
	}

}
