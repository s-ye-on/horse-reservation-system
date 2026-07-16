package com.horse.reservations.application;

import java.util.List;

public record BulkReservationAttendanceResult(
	int requestedCount,
	int succeededCount,
	int failedCount,
	List<BulkReservationAttendanceItemResult> items
) {

	public static BulkReservationAttendanceResult from(List<BulkReservationAttendanceItemResult> items) {
		final List<BulkReservationAttendanceItemResult> copiedItems = List.copyOf(items);
		final int succeededCount = (int) copiedItems.stream()
			.filter(BulkReservationAttendanceItemResult::success)
			.count();
		return new BulkReservationAttendanceResult(
			copiedItems.size(),
			succeededCount,
			copiedItems.size() - succeededCount,
			copiedItems);
	}
}
