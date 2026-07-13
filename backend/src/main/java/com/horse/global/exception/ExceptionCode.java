package com.horse.global.exception;

import org.springframework.http.HttpStatus;

public enum ExceptionCode {

	MEMBER_INVALID_AUTH_SUBJECT(HttpStatus.BAD_REQUEST, "회원 인증 주체는 필수입니다."),
	MEMBER_INVALID_NAME(HttpStatus.BAD_REQUEST, "회원 이름은 필수입니다."),
	MEMBER_INVALID_PHONE(HttpStatus.BAD_REQUEST, "회원 전화번호는 필수입니다."),
	MEMBER_INVALID_GENERAL_RIDE_COUNT(HttpStatus.BAD_REQUEST, "일반 기승 횟수는 음수일 수 없습니다.");

	private final HttpStatus status;
	private final String message;

	ExceptionCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public String code() {
		return name();
	}

	public String message() {
		return message;
	}

	public HttpStatus status() {
		return status;
	}

}
