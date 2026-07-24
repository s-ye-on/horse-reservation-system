package com.horse.reservations.domain;

import java.time.LocalTime;
import java.util.Collection;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationIntervalOverlapPolicy {

	private ReservationIntervalOverlapPolicy() {
	}

	public static void ensureNoOverlap(Collection<Reservation> overlappingReservations) {
		if (!overlappingReservations.isEmpty()) {
			throw new ReservationException(ExceptionCode.RESERVATION_OVERLAPPING_ACTIVE_RESERVATION);
		}
	}

	public static boolean overlaps(
		LocalTime existingStartTime,
		LocalTime existingEndTime,
		LocalTime candidateStartTime,
		LocalTime candidateEndTime
	) {
		return existingStartTime.isBefore(candidateEndTime)
			&& candidateStartTime.isBefore(existingEndTime);
	}
}
