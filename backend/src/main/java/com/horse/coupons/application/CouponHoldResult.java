package com.horse.coupons.application;

import com.horse.coupons.domain.Coupon;

public record CouponHoldResult(
	Long couponId,
	Long reservationId,
	int remainingCount,
	int heldCount,
	int availableCount,
	boolean changed
) {

	public static CouponHoldResult from(Coupon coupon, Long reservationId, boolean changed) {
		return new CouponHoldResult(
			coupon.getId(),
			reservationId,
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getRemainingCount() - coupon.getHeldCount(),
			changed);
	}
}
