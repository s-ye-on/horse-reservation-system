package com.horse.reservations.application;

public record IdempotentReservationApplicationResult(
	int httpStatus,
	String responseBody
) {
}
