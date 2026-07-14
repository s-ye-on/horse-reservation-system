package com.horse.reservations.application;

import java.time.LocalDateTime;

import com.horse.coupons.application.CouponHoldResult;
import com.horse.coupons.application.CouponSelectionResult;

public record ReservationCouponResult(
	Long couponId,
	LocalDateTime expiresAt,
	int remainingCount,
	int heldCount,
	int availableCount
) {

	public static ReservationCouponResult from(
		CouponSelectionResult selection,
		CouponHoldResult hold
	) {
		return new ReservationCouponResult(
			hold.couponId(),
			selection.expiresAt(),
			hold.remainingCount(),
			hold.heldCount(),
			hold.availableCount());
	}
}
