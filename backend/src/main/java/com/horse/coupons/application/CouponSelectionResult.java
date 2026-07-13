package com.horse.coupons.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record CouponSelectionResult(
	Long couponId,
	String type,
	int remainingCount,
	int heldCount,
	int availableCount,
	LocalDateTime expiresAt
) {

	public static CouponSelectionResult from(Coupon coupon) {
		return new CouponSelectionResult(
			coupon.getId(),
			coupon.getType().value(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getRemainingCount() - coupon.getHeldCount(),
			coupon.getExpiresAt());
	}
}
