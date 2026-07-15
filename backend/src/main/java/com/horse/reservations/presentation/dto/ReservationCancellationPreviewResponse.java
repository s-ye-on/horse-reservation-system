package com.horse.reservations.presentation.dto;

import java.util.Locale;

import com.horse.reservations.application.ReservationCancellationPreviewResult;

public record ReservationCancellationPreviewResponse(
	Long reservationId,
	String timing,
	String responsibility,
	String couponAction
) {

	public static ReservationCancellationPreviewResponse from(
		ReservationCancellationPreviewResult result
	) {
		return new ReservationCancellationPreviewResponse(
			result.reservationId(),
			result.timing().name().toLowerCase(Locale.ROOT),
			result.responsibility().databaseValue(),
			result.couponAction().databaseValue());
	}
}
