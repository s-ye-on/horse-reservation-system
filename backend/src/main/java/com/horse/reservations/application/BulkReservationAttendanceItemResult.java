package com.horse.reservations.application;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public record BulkReservationAttendanceItemResult(
	Long reservationId,
	String action,
	boolean success,
	String status,
	String errorCode,
	String errorMessage
) {

	public static BulkReservationAttendanceItemResult success(Long reservationId, String action, String status) {
		return new BulkReservationAttendanceItemResult(reservationId, action, true, status, "", "");
	}

	public static BulkReservationAttendanceItemResult failure(
		Long reservationId,
		String action,
		BusinessException exception
	) {
		return new BulkReservationAttendanceItemResult(
			reservationId,
			action,
			false,
			"",
			exception.code(),
			exception.getMessage());
	}

	public static BulkReservationAttendanceItemResult unexpectedFailure(Long reservationId, String action) {
		return new BulkReservationAttendanceItemResult(
			reservationId,
			action,
			false,
			"",
			ExceptionCode.COMMON_INTERNAL_ERROR.code(),
			ExceptionCode.COMMON_INTERNAL_ERROR.message());
	}
}
