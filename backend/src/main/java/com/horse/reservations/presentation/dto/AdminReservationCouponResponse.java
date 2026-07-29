package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.AdminReservationCouponResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminReservationCouponResponse(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	@Schema(nullable = true) OffsetDateTime expiresAt
) {

	public static AdminReservationCouponResponse from(AdminReservationCouponResult result) {
		return new AdminReservationCouponResponse(
			result.couponId(),
			result.couponType(),
			result.status(),
			result.remainingCount(),
			result.heldCount(),
			ApiDateTime.toSeoulOffset(result.expiresAt()));
	}
}
