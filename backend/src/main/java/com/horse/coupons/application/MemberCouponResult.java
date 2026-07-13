package com.horse.coupons.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record MemberCouponResult(
	Long couponId,
	String type,
	int totalCount,
	int remainingCount,
	int heldCount,
	int availableCount,
	LocalDateTime firstUsedAt,
	LocalDateTime expiresAt,
	boolean freeChangeUsed,
	String status
) {

	public static MemberCouponResult from(Coupon coupon) {
		return new MemberCouponResult(
			coupon.getId(),
			coupon.getType().value(),
			coupon.getTotalCount(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getRemainingCount() - coupon.getHeldCount(),
			coupon.getFirstUsedAt(),
			coupon.getExpiresAt(),
			coupon.isFreeChangeUsed(),
			coupon.getStatus().value());
	}
}
