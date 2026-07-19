package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationAction;

public record ReservationActionsResult(
	ReservationActionAvailabilityResult change,
	ReservationActionAvailabilityResult cancel,
	ReservationActionAvailabilityResult complete,
	ReservationActionAvailabilityResult noShow,
	ReservationActionAvailabilityResult approve
) {

	public static ReservationActionsResult from(Reservation reservation, LocalDateTime actionAt) {
		return new ReservationActionsResult(
			availability(reservation, ReservationAction.CHANGE, actionAt),
			availability(reservation, ReservationAction.CANCEL, actionAt),
			availability(reservation, ReservationAction.COMPLETE, actionAt),
			availability(reservation, ReservationAction.NO_SHOW, actionAt),
			availability(reservation, ReservationAction.APPROVE, actionAt));
	}

	private static ReservationActionAvailabilityResult availability(
		Reservation reservation,
		ReservationAction action,
		LocalDateTime actionAt
	) {
		return ReservationActionAvailabilityResult.from(
			reservation.actionAvailability(action, actionAt));
	}
}
