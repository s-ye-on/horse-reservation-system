package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.AdminReservationCouponResult;

public record AdminReservationCouponResponse(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	LocalDateTime expiresAt
) {

	public static AdminReservationCouponResponse from(AdminReservationCouponResult result) {
		return new AdminReservationCouponResponse(
			result.couponId(),
			result.couponType(),
			result.status(),
			result.remainingCount(),
			result.heldCount(),
			result.expiresAt());
	}
}
