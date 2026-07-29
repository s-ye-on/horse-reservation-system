package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.reservations.application.ReservationChangeResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationChangeResponse(
	Long reservationId,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	String status,
	String couponAction,
	boolean freeChangeUsed,
	boolean changed
) {

	public static ReservationChangeResponse from(ReservationChangeResult result) {
		return new ReservationChangeResponse(
			result.reservationId(),
			result.lessonDate(),
			result.startTime(),
			result.status().databaseValue(),
			result.couponAction().databaseValue(),
			result.freeChangeUsed(),
			result.changed());
	}
}
