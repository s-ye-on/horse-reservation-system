package com.horse.reservations.application;

import com.horse.reservations.domain.ReservationStatus;

public record ReservationStatusCountResult(
	ReservationStatus status,
	long count
) {
}
