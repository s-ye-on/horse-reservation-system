package com.horse.global.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	protected BusinessException(ExceptionCode exceptionCode) {
		super(exceptionCode.message());
		this.code = exceptionCode.code();
		this.status = exceptionCode.status();
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

}
