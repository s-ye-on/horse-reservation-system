package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.Coupon;

public record MemberReservationCouponResult(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	int availableCount,
	LocalDateTime expiresAt
) {

	public static MemberReservationCouponResult from(Coupon coupon) {
		return new MemberReservationCouponResult(
			coupon.getId(),
			coupon.getType().value(),
			coupon.getStatus().value(),
			coupon.getRemainingCount(),
			coupon.getHeldCount(),
			coupon.getRemainingCount() - coupon.getHeldCount(),
			coupon.getExpiresAt());
	}
}
