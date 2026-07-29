package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.BulkReservationAttendanceItemResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record BulkReservationAttendanceItemResponse(
	Long reservationId,
	String action,
	boolean success,
	@Schema(nullable = true) String status,
	@Schema(nullable = true) String errorCode,
	@Schema(nullable = true) String errorMessage
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
