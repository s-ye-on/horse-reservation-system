package com.horse.reservations.presentation.dto;

public record AdminReservationChangeRequest(
	Long targetTimeSlotId,
	String memo
) {
}
