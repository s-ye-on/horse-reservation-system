package com.horse.global.exception;

import org.springframework.http.HttpStatus;

public enum ExceptionCode {

	MEMBER_INVALID_AUTH_SUBJECT(HttpStatus.BAD_REQUEST, "회원 인증 주체는 필수입니다."),
	MEMBER_INVALID_NAME(HttpStatus.BAD_REQUEST, "회원 이름은 필수입니다."),
	MEMBER_INVALID_PHONE(HttpStatus.BAD_REQUEST, "회원 전화번호는 필수입니다."),
	MEMBER_INVALID_GENERAL_RIDE_COUNT(HttpStatus.BAD_REQUEST, "일반 기승 횟수는 음수일 수 없습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

	RESERVATION_INVALID_MEMBER_ID(HttpStatus.BAD_REQUEST, "예약 회원 식별자는 필수입니다."),
	RESERVATION_INVALID_RIDING_CLASS(HttpStatus.BAD_REQUEST, "예약 클래스는 필수입니다."),
	RESERVATION_INVALID_LESSON_DATE(HttpStatus.BAD_REQUEST, "예약 수업 날짜는 필수입니다."),
	RESERVATION_INVALID_START_TIME(HttpStatus.BAD_REQUEST, "예약 수업 시작 시각은 필수입니다."),
	RESERVATION_INVALID_CHANGE_REQUESTED_AT(HttpStatus.BAD_REQUEST, "예약 변경 요청 시각은 필수입니다."),
	RESERVATION_INVALID_CHANGE_REASON(HttpStatus.BAD_REQUEST, "예약 변경 사유가 올바르지 않습니다."),
	RESERVATION_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 시각과 예약 조건에서는 변경할 수 없습니다."),
	RESERVATION_CHANGE_SOURCE_CONFLICT(HttpStatus.CONFLICT, "예약 일정이 이미 변경되었습니다. 최신 예약을 확인해 주세요."),
	RESERVATION_MEMBER_MISMATCH(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
	RESERVATION_INVALID_COUPON_ID(HttpStatus.BAD_REQUEST, "쿠폰 예약에는 쿠폰 식별자가 필요합니다."),
	RESERVATION_INVALID_PAYMENT_DUE_AT(HttpStatus.BAD_REQUEST, "입금대기 예약에는 입금 마감 시각이 필요합니다."),
	RESERVATION_INVALID_APPROVAL_REQUESTED_AT(HttpStatus.BAD_REQUEST, "예약 신청 시각은 필수입니다."),
	RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
	RESERVATION_INVALID_STATUS(HttpStatus.CONFLICT, "현재 예약 상태에서는 요청한 처리를 할 수 없습니다."),
	RESERVATION_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "입금 마감 시각이 지난 예약은 확정할 수 없습니다."),
	RESERVATION_INVALID_REJECTION_ACTOR(HttpStatus.BAD_REQUEST, "예약 반려 관리자 식별자는 필수입니다."),
	RESERVATION_INVALID_REJECTION_REASON(HttpStatus.BAD_REQUEST, "예약 반려 사유가 올바르지 않습니다."),
	RESERVATION_INVALID_PAYMENT_EXPIRY_AT(HttpStatus.BAD_REQUEST, "입금대기 만료 처리 시각은 필수입니다."),
	RESERVATION_INVALID_PAYMENT_RESTORE_AT(HttpStatus.BAD_REQUEST, "입금대기 복구 처리 시각은 필수입니다."),
	RESERVATION_INVALID_CHANGE_LOG_REFERENCE(HttpStatus.BAD_REQUEST, "예약 변경 이력 참조가 올바르지 않습니다."),
	RESERVATION_INVALID_CHANGE_LOG_ACTOR(HttpStatus.BAD_REQUEST, "예약 변경 이력 처리 주체가 올바르지 않습니다."),
	RESERVATION_INVALID_CHANGE_LOG_MEMO(HttpStatus.BAD_REQUEST, "예약 변경 이력 메모가 올바르지 않습니다."),
	RESERVATION_INVALID_COUPON_ACTION(HttpStatus.BAD_REQUEST, "예약 유형에 허용되지 않는 쿠폰 처리입니다."),
	RESERVATION_INVALID_ADMIN_MEMO(HttpStatus.BAD_REQUEST, "관리자 처리 메모가 올바르지 않습니다."),
	RESERVATION_INVALID_QUERY_STATUS(HttpStatus.BAD_REQUEST, "조회할 예약 상태가 올바르지 않습니다."),
	RESERVATION_INVALID_QUERY_CLASS_TYPE(HttpStatus.BAD_REQUEST, "조회할 예약 클래스가 올바르지 않습니다."),
	RESERVATION_INVALID_QUERY_DATE_RANGE(HttpStatus.BAD_REQUEST, "예약 조회 날짜 범위가 올바르지 않습니다."),
	RESERVATION_INVALID_QUERY_PAGE(HttpStatus.BAD_REQUEST, "예약 조회 페이지가 올바르지 않습니다."),
	RESERVATION_INVALID_PERSISTED_VALUE(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 예약 값이 올바르지 않습니다."),

	COUPON_INVALID_MEMBER_ID(HttpStatus.BAD_REQUEST, "쿠폰 회원 식별자는 필수입니다."),
	COUPON_INVALID_TYPE(HttpStatus.BAD_REQUEST, "쿠폰 종류가 올바르지 않습니다."),
	COUPON_INVALID_TOTAL_COUNT(HttpStatus.BAD_REQUEST, "쿠폰은 10회권만 등록할 수 있습니다."),
	COUPON_INVALID_CREATED_BY(HttpStatus.BAD_REQUEST, "쿠폰 등록 관리자 식별자는 필수입니다."),
	COUPON_INVALID_LESSON_DATE(HttpStatus.BAD_REQUEST, "쿠폰을 사용할 수업 날짜는 필수입니다."),
	COUPON_INVALID_EXPIRY_DATE(HttpStatus.BAD_REQUEST, "쿠폰 만료 기준 날짜는 필수입니다."),
	COUPON_INVALID_USAGE_REFERENCE(HttpStatus.BAD_REQUEST, "쿠폰 사용 이력 참조가 올바르지 않습니다."),
	COUPON_INVALID_USAGE_OCCURRED_AT(HttpStatus.BAD_REQUEST, "쿠폰 사용 이력 발생 시각은 필수입니다."),
	COUPON_INVALID_ACTOR_TYPE(HttpStatus.BAD_REQUEST, "쿠폰 처리 주체는 필수입니다."),
	COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
	COUPON_HOLD_NOT_AVAILABLE(HttpStatus.CONFLICT, "쿠폰에 사용 가능한 횟수가 없습니다."),
	COUPON_EXPIRED_FOR_LESSON(HttpStatus.CONFLICT, "수업일에 만료된 쿠폰은 사용할 수 없습니다."),
	COUPON_HOLD_STATE_CONFLICT(HttpStatus.CONFLICT, "쿠폰 임시 점유 상태가 일치하지 않습니다."),
	COUPON_INVALID_PERSISTED_VALUE(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 쿠폰 값이 올바르지 않습니다."),

	TIMESLOT_INVALID_LESSON_DATE(HttpStatus.BAD_REQUEST, "수업 날짜는 필수입니다."),
	TIMESLOT_INVALID_START_TIME(HttpStatus.BAD_REQUEST, "수업 시작 시각은 필수입니다."),
	TIMESLOT_INVALID_QUERY_DATE(HttpStatus.BAD_REQUEST, "조회 날짜는 오늘 이후의 유효한 날짜여야 합니다."),
	TIMESLOT_INVALID_CLASS_TYPE(HttpStatus.BAD_REQUEST, "조회할 클래스가 올바르지 않습니다."),
	TIMESLOT_INVALID_TOTAL_CAPACITY(HttpStatus.BAD_REQUEST, "전체 정원은 0명 이상 8명 이하여야 합니다."),
	TIMESLOT_INVALID_ROUND_ARENA_CAPACITY(HttpStatus.BAD_REQUEST, "원형 정원은 전체 정원 이하이며 4명을 넘을 수 없습니다."),
	TIMESLOT_INVALID_CLASS_CAPACITIES(HttpStatus.BAD_REQUEST, "클래스별 정원이 올바르지 않습니다."),
	TIMESLOT_INVALID_CLOSED_STATUS(HttpStatus.BAD_REQUEST, "시간대 마감 상태는 필수입니다."),
	TIMESLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 날짜와 시작 시각의 시간대가 이미 존재합니다."),
	TIMESLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "시간대를 찾을 수 없습니다."),
	TIMESLOT_CLOSED(HttpStatus.CONFLICT, "마감된 시간대에는 예약할 수 없습니다."),
	TIMESLOT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "시간대 정원이 마감되었습니다."),
	TIMESLOT_CAPACITY_BELOW_OCCUPANCY(HttpStatus.CONFLICT, "현재 예약 인원보다 정원을 작게 설정할 수 없습니다."),
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
