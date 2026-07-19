package com.horse.reservations.presentation.dto;

import com.horse.reservations.application.ReservationActionsResult;

public record ReservationActionsResponse(
	ReservationActionAvailabilityResponse change,
	ReservationActionAvailabilityResponse cancel,
	ReservationActionAvailabilityResponse complete,
	ReservationActionAvailabilityResponse noShow,
	ReservationActionAvailabilityResponse approve
) {

	public static ReservationActionsResponse from(ReservationActionsResult result) {
		return new ReservationActionsResponse(
			ReservationActionAvailabilityResponse.from(result.change()),
			ReservationActionAvailabilityResponse.from(result.cancel()),
			ReservationActionAvailabilityResponse.from(result.complete()),
			ReservationActionAvailabilityResponse.from(result.noShow()),
			ReservationActionAvailabilityResponse.from(result.approve()));
	}
}
