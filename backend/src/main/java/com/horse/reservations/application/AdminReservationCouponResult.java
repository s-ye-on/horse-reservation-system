package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record AdminReservationCouponResult(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	LocalDateTime expiresAt
) {

	public static AdminReservationCouponResult from(Coupon coupon) {
		return new AdminReservationCouponResult(
			coupon.getId(),
			coupon.getType().value(),
			coupon.getStatus().value(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getExpiresAt());
	}
}
