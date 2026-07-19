package com.horse.reservations.domain;

public record ReservationActionAvailability(
	boolean allowed,
	String blockedReason
) {

	public static ReservationActionAvailability allow() {
		return new ReservationActionAvailability(true, null);
	}

	public static ReservationActionAvailability block(String blockedReason) {
		return new ReservationActionAvailability(false, blockedReason);
	}
}
