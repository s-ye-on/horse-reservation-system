package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BulkReservationAttendanceItemRequest(
	@NotNull
	@Positive
	Long reservationId,
	@NotBlank
	String action,
	String couponAction,
	String memo
) {
}
