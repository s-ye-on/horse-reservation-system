package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationRejectResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationRejectResponse(
	Long reservationId,
	String status,
	String paymentSource,
	@Schema(nullable = true) Long couponId,
	OffsetDateTime rejectedAt,
	String rejectedBy,
	String rejectionReason
) {

	public static ReservationRejectResponse from(ReservationRejectResult result) {
		return new ReservationRejectResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			ApiDateTime.toSeoulOffset(result.rejectedAt()),
			result.rejectedBy(),
			result.rejectionReason());
	}
}
