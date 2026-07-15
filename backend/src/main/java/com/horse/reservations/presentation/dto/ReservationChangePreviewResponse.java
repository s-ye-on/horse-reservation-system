package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import com.horse.reservations.application.ReservationChangePreviewResult;

public record ReservationChangePreviewResponse(
	Long reservationId,
	Long targetTimeSlotId,
	LocalDate targetLessonDate,
	LocalTime targetStartTime,
	String timing,
	String couponAction,
	boolean freeChangeUsed
) {

	public static ReservationChangePreviewResponse from(ReservationChangePreviewResult result) {
		return new ReservationChangePreviewResponse(
			result.reservationId(),
			result.targetTimeSlotId(),
			result.targetLessonDate(),
			result.targetStartTime(),
			result.timing().name().toLowerCase(Locale.ROOT),
			result.couponAction().databaseValue(),
			result.freeChangeUsed());
	}
}
