package com.horse.reservations.presentation.dto;

public record ReservationApplicationRequest(
	Long timeSlotId,
	String classType
) {
}
