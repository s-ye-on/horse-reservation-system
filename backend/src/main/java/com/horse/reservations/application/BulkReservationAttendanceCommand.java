package com.horse.reservations.application;

public record BulkReservationAttendanceCommand(
	Long reservationId,
	String action,
	String couponAction,
	String memo
) {
}
