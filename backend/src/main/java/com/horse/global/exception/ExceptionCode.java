package com.horse.global.exception;

import org.springframework.http.HttpStatus;

public enum ExceptionCode {

	MEMBER_INVALID_AUTH_SUBJECT(HttpStatus.BAD_REQUEST, "회원 인증 주체는 필수입니다."),
	MEMBER_INVALID_NAME(HttpStatus.BAD_REQUEST, "회원 이름은 필수입니다."),
	MEMBER_INVALID_PHONE(HttpStatus.BAD_REQUEST, "회원 전화번호는 필수입니다."),
	MEMBER_INVALID_GENERAL_RIDE_COUNT(HttpStatus.BAD_REQUEST, "일반 기승 횟수는 음수일 수 없습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

	TIMESLOT_INVALID_LESSON_DATE(HttpStatus.BAD_REQUEST, "수업 날짜는 필수입니다."),
	TIMESLOT_INVALID_START_TIME(HttpStatus.BAD_REQUEST, "수업 시작 시각은 필수입니다."),
	TIMESLOT_INVALID_TOTAL_CAPACITY(HttpStatus.BAD_REQUEST, "전체 정원은 0명 이상 8명 이하여야 합니다."),
	TIMESLOT_INVALID_ROUND_ARENA_CAPACITY(HttpStatus.BAD_REQUEST, "원형 정원은 전체 정원 이하이며 4명을 넘을 수 없습니다."),
	TIMESLOT_INVALID_CLASS_CAPACITIES(HttpStatus.BAD_REQUEST, "클래스별 정원이 올바르지 않습니다."),
	TIMESLOT_INVALID_CLOSED_STATUS(HttpStatus.BAD_REQUEST, "시간대 마감 상태는 필수입니다."),
	TIMESLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 날짜와 시작 시각의 시간대가 이미 존재합니다."),
	TIMESLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "시간대를 찾을 수 없습니다."),
	TIMESLOT_RESERVATION_HISTORY_EXISTS(HttpStatus.CONFLICT, "예약 이력이 있는 시간대는 삭제할 수 없습니다.");

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
