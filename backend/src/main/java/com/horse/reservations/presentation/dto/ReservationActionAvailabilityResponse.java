package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationActionAvailabilityResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationActionAvailabilityResponse(
	boolean allowed,
	@Schema(nullable = true) String blockedReason
) {

	public static ReservationActionAvailabilityResponse from(
		ReservationActionAvailabilityResult result
	) {
		return new ReservationActionAvailabilityResponse(
			result.allowed(), result.blockedReason());
	}
}
