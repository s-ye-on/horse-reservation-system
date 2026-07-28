package com.horse.global.exception;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.converter.HttpMessageNotReadableException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final String DEFAULT_FIELD_ERROR_MESSAGE = "올바르지 않은 값입니다.";
	private final ErrorResponseWriter errorResponseWriter;

	public GlobalExceptionHandler(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@ExceptionHandler(BusinessException.class)
	void handleBusinessException(
		BusinessException exception,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(exception, request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	void handleMethodArgumentNotValidException(
		MethodArgumentNotValidException exception,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		writeValidationException(exception, request, response);
	}

	@ExceptionHandler(BindException.class)
	void handleBindException(
		BindException exception,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		writeValidationException(exception, request, response);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	void handleHttpMessageNotReadableException(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.validation(request.getRequestURI(), List.of()));
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	void handleParameterException(
		Exception exception,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		final String field = switch (exception) {
			case MethodArgumentTypeMismatchException mismatchException -> mismatchException.getName();
			case MissingServletRequestParameterException missingParameterException ->
				missingParameterException.getParameterName();
			default -> throw new IllegalStateException("지원하지 않는 요청 파라미터 예외입니다.");
		};
		errorResponseWriter.write(response, ErrorResponse.validation(
			request.getRequestURI(),
			List.of(new ErrorResponse.FieldError(field, DEFAULT_FIELD_ERROR_MESSAGE))
		));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	void handleNoResourceFoundException(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(ExceptionCode.COMMON_NOT_FOUND, request.getRequestURI()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	void handleHttpRequestMethodNotSupportedException(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(
			ExceptionCode.COMMON_METHOD_NOT_ALLOWED,
			request.getRequestURI()
		));
	}

	@ExceptionHandler(Exception.class)
	void handleUnexpectedException(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(
			ExceptionCode.COMMON_INTERNAL_ERROR,
			request.getRequestURI()
		));
	}

	private void writeValidationException(
		BindException exception,
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException {
		final List<ErrorResponse.FieldError> fieldErrors = exception.getFieldErrors().stream()
			.map(error -> new ErrorResponse.FieldError(
				error.getField(),
				Objects.requireNonNullElse(error.getDefaultMessage(), DEFAULT_FIELD_ERROR_MESSAGE)
			))
			.sorted(Comparator.comparing(ErrorResponse.FieldError::field)
				.thenComparing(ErrorResponse.FieldError::message))
			.toList();
		errorResponseWriter.write(response, ErrorResponse.validation(request.getRequestURI(), fieldErrors));
	}

}
