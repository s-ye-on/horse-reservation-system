package com.horse.reservations.application;

import com.horse.reservations.domain.ReservationActionAvailability;

public record ReservationActionAvailabilityResult(
	boolean allowed,
	String blockedReason
) {

	public static ReservationActionAvailabilityResult from(ReservationActionAvailability availability) {
		return new ReservationActionAvailabilityResult(
			availability.allowed(), availability.blockedReason());
	}
}
