package com.horse.coupons.presentation.dto;

import java.time.LocalDateTime;

import com.horse.coupons.application.CouponRegistrationResult;

public record CouponResponse(
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

	public static CouponResponse from(CouponRegistrationResult result) {
		return new CouponResponse(
			result.id(),
			result.memberId(),
			result.type(),
			result.totalCount(),
			result.remainingCount(),
			result.heldCount(),
			result.firstUsedAt(),
			result.expiresAt(),
			result.freeChangeUsed(),
			result.status(),
			result.createdBy(),
			result.createdAt());
	}
}
