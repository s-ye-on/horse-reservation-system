package com.horse.coupons.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record CouponRegistrationResult(
	Long id,
	Long memberId,
	String type,
	int totalCount,
	int remainingCount,
	int heldCount,
	LocalDateTime firstUsedAt,
	LocalDateTime expiresAt,
	boolean freeChangeUsed,
	String status,
	String createdBy,
	LocalDateTime createdAt
) {

	public static CouponRegistrationResult from(Coupon coupon) {
		return new CouponRegistrationResult(
			coupon.getId(),
			coupon.getMemberId(),
			coupon.getType().value(),
			coupon.getTotalCount(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getFirstUsedAt(),
			coupon.getExpiresAt(),
			coupon.isFreeChangeUsed(),
			coupon.getStatus().value(),
			coupon.getCreatedBy(),
			coupon.getCreatedAt());
	}
}
