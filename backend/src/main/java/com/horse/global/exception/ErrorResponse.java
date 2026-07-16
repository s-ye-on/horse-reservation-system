package com.horse.global.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
	String code,
	String message,
	int status,
	OffsetDateTime timestamp,
	String path,
	List<FieldError> fieldErrors
) {

	public ErrorResponse {
		fieldErrors = List.copyOf(fieldErrors);
	}

    // 비즈니스 예외용
	public static ErrorResponse from(BusinessException exception, String path) {
		return new ErrorResponse(
			exception.code(),
			exception.getMessage(),
			exception.status().value(),
			OffsetDateTime.now(),
			path,
			List.of()
		);
	}

    // 검증 에러용 @Valid
	public static ErrorResponse validation(String path, List<FieldError> fieldErrors) {
		final ExceptionCode exceptionCode = ExceptionCode.COMMON_INVALID_REQUEST;
		return new ErrorResponse(
			exceptionCode.code(),
			exceptionCode.message(),
			exceptionCode.status().value(),
			OffsetDateTime.now(),
			path,
			fieldErrors
		);
	}

	public record FieldError(String field, String message) {
	}
}
