package com.horse.coupons.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record CouponSelectionResult(
	Long couponId,
	Long couponOwnerMemberId,
	Long familyGroupId,
	String type,
	int remainingCount,
	int heldCount,
	int availableCount,
	LocalDateTime expiresAt
) {

	public static CouponSelectionResult from(Coupon coupon, Long familyGroupId) {
		return new CouponSelectionResult(
			coupon.getId(),
			coupon.getMemberId(),
			familyGroupId,
			coupon.getType().value(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getRemainingCount() - coupon.getHeldCount(),
			coupon.getExpiresAt());
	}
}
