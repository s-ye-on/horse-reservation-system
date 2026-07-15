package com.horse.coupons.application;

public record CouponExpiryResult(
	int expiredCouponCount,
	int expiredAvailableCount
) {
}
