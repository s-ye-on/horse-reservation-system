package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationCancelResult;

public record ReservationCancelResponse(
	Long reservationId,
	String status,
	String responsibility,
	String couponAction,
	OffsetDateTime cancelledAt,
	boolean changed
) {

	public static ReservationCancelResponse from(ReservationCancelResult result) {
		return new ReservationCancelResponse(
			result.reservationId(),
			result.status().databaseValue(),
			result.responsibility().databaseValue(),
			result.couponAction().databaseValue(),
			ApiDateTime.toSeoulOffset(result.cancelledAt()),
			result.changed());
	}
}
