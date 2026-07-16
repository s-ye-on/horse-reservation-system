package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.BulkReservationAttendanceItemResult;

public record BulkReservationAttendanceItemResponse(
	Long reservationId,
	String action,
	boolean success,
	String status,
	String errorCode,
	String errorMessage
) {

	public static BulkReservationAttendanceItemResponse from(BulkReservationAttendanceItemResult result) {
		return new BulkReservationAttendanceItemResponse(
			result.reservationId(),
			result.action(),
			result.success(),
			result.status(),
			result.errorCode(),
			result.errorMessage());
	}
}
