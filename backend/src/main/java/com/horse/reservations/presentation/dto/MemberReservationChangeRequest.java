package com.horse.reservations.presentation.dto;

public record MemberReservationChangeRequest(
	Long targetTimeSlotId,
	String reason
) {
}
