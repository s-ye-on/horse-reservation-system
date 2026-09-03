package com.horse.reservations.application;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.WeeklyOperationsReservationProjection;

public record AdminWeeklyOperationsReservationResult(
	Long reservationId,
	Long memberId,
	String memberName,
	RidingClass ridingClass,
	ReservationStatus status
) {

	public static AdminWeeklyOperationsReservationResult from(
		WeeklyOperationsReservationProjection projection
	) {
		return new AdminWeeklyOperationsReservationResult(
			projection.getReservationId(),
			projection.getMemberId(),
			projection.getMemberName(),
			projection.getRidingClass(),
			projection.getStatus());
	}

}
