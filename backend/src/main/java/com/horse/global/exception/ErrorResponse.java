package com.horse.global.exception;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"code", "message", "status", "timestamp", "path", "fieldErrors", "details"
})
public record ErrorResponse(
	String code,
	String message,
	int status,
	OffsetDateTime timestamp,
	String path,
	List<FieldError> fieldErrors,
	@Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
	Map<String, Object> details
) {

	public ErrorResponse {
		fieldErrors = List.copyOf(fieldErrors);
		details = Map.copyOf(details);
	}

    // 비즈니스 예외용
	public static ErrorResponse from(BusinessException exception, String path) {
		return new ErrorResponse(
			exception.code(),
			exception.getMessage(),
			exception.status().value(),
			OffsetDateTime.now(),
			path,
			List.of(),
			exception.details()
		);
	}

	public static ErrorResponse from(ExceptionCode exceptionCode, String path) {
		return new ErrorResponse(
			exceptionCode.code(),
			exceptionCode.message(),
			exceptionCode.status().value(),
			OffsetDateTime.now(),
			path,
			List.of(),
			Map.of()
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
			fieldErrors,
			Map.of()
		);
	}

	public record FieldError(String field, String message) {
	}
}
