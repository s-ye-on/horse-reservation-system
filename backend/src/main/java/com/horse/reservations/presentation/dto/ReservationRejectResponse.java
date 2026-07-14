package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.ReservationRejectResult;

public record ReservationRejectResponse(
	Long reservationId,
	String status,
	String paymentSource,
	Long couponId,
	LocalDateTime rejectedAt,
	String rejectedBy,
	String rejectionReason
) {

	public static ReservationRejectResponse from(ReservationRejectResult result) {
		return new ReservationRejectResponse(
			result.reservationId(),
			result.status(),
			result.paymentSource(),
			result.couponId(),
			result.rejectedAt(),
			result.rejectedBy(),
			result.rejectionReason());
	}
}
