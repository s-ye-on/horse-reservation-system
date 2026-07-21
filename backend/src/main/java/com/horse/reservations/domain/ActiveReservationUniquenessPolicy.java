package com.horse.reservations.domain;

import java.util.Collection;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ActiveReservationUniquenessPolicy {

	private ActiveReservationUniquenessPolicy() {
	}

	public static void ensureNoDuplicate(Long memberId, Collection<Reservation> activeReservations) {
		if (activeReservations.stream().anyMatch(reservation -> reservation.getMemberId().equals(memberId))) {
			throw new ReservationException(ExceptionCode.RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT);
		}
	}

}
