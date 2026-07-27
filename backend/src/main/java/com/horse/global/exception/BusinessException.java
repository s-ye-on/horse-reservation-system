package com.horse.global.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> details;

	protected BusinessException(ExceptionCode exceptionCode) {
		this(exceptionCode, Map.of());
	}

	protected BusinessException(ExceptionCode exceptionCode, Map<String, Object> details) {
		super(exceptionCode.message());
		this.code = exceptionCode.code();
		this.status = exceptionCode.status();
		this.details = Map.copyOf(details);
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

	public Map<String, Object> details() {
		return details;
	}

}
