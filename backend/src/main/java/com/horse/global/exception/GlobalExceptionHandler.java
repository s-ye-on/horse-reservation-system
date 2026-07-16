package com.horse.global.exception;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final String DEFAULT_FIELD_ERROR_MESSAGE = "올바르지 않은 값입니다.";

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusinessException(
		BusinessException exception,
		HttpServletRequest request
	) {
		final ErrorResponse response = ErrorResponse.from(exception, request.getRequestURI());
		return ResponseEntity.status(exception.status()).body(response);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		return handleValidationException(exception, request);
	}

	@ExceptionHandler(BindException.class)
	ResponseEntity<ErrorResponse> handleBindException(
		BindException exception,
		HttpServletRequest request
	) {
		return handleValidationException(exception, request);
	}

	private ResponseEntity<ErrorResponse> handleValidationException(
		BindException exception,
		HttpServletRequest request
	) {
		final List<ErrorResponse.FieldError> fieldErrors = exception.getFieldErrors().stream()
			.map(error -> new ErrorResponse.FieldError(
				error.getField(),
				Objects.requireNonNullElse(error.getDefaultMessage(), DEFAULT_FIELD_ERROR_MESSAGE)
			))
			.sorted(Comparator.comparing(ErrorResponse.FieldError::field)
				.thenComparing(ErrorResponse.FieldError::message))
			.toList();
		final ErrorResponse response = ErrorResponse.validation(request.getRequestURI(), fieldErrors);
		return ResponseEntity.badRequest().body(response);
	}

}
