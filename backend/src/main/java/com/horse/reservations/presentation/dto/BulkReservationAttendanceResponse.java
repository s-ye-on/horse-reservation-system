package com.horse.reservations.presentation.dto;

import java.util.List;

import com.horse.reservations.application.BulkReservationAttendanceResult;

public record BulkReservationAttendanceResponse(
	int requestedCount,
	int succeededCount,
	int failedCount,
	List<BulkReservationAttendanceItemResponse> items
) {

	public static BulkReservationAttendanceResponse from(BulkReservationAttendanceResult result) {
		return new BulkReservationAttendanceResponse(
			result.requestedCount(),
			result.succeededCount(),
			result.failedCount(),
			result.items().stream()
				.map(BulkReservationAttendanceItemResponse::from)
				.toList());
	}
}
