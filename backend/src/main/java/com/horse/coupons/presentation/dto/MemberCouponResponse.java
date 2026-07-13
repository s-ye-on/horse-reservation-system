package com.horse.coupons.presentation.dto;

import java.time.LocalDateTime;

import com.horse.coupons.application.MemberCouponResult;

public record MemberCouponResponse(
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

	public static MemberCouponResponse from(MemberCouponResult result) {
		return new MemberCouponResponse(
			result.couponId(),
			result.type(),
			result.totalCount(),
			result.remainingCount(),
			result.heldCount(),
			result.availableCount(),
			result.firstUsedAt(),
			result.expiresAt(),
			result.freeChangeUsed(),
			result.status());
	}
}
