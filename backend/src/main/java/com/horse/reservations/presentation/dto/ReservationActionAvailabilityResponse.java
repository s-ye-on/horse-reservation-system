package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationActionAvailabilityResult;

public record ReservationActionAvailabilityResponse(
	boolean allowed,
	String blockedReason
) {

	public static ReservationActionAvailabilityResponse from(
		ReservationActionAvailabilityResult result
	) {
		return new ReservationActionAvailabilityResponse(
			result.allowed(), result.blockedReason());
	}
}
