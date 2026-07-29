package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationCouponResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationCouponResponse(
	Long couponId,
	@Schema(nullable = true) OffsetDateTime expiresAt,
	int remainingCount,
	int heldCount,
	int availableCount
) {

	public static ReservationCouponResponse from(ReservationCouponResult result) {
		return new ReservationCouponResponse(
			result.couponId(),
			ApiDateTime.toSeoulOffset(result.expiresAt()),
			result.remainingCount(),
			result.heldCount(),
			result.availableCount());
	}
}
