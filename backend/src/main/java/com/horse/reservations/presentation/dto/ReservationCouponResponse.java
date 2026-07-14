package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.ReservationCouponResult;

public record ReservationCouponResponse(
	Long couponId,
	LocalDateTime expiresAt,
	int remainingCount,
	int heldCount,
	int availableCount
) {

	public static ReservationCouponResponse from(ReservationCouponResult result) {
		return new ReservationCouponResponse(
			result.couponId(),
			result.expiresAt(),
			result.remainingCount(),
			result.heldCount(),
			result.availableCount());
	}
}
