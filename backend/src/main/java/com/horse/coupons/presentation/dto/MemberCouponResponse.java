package com.horse.coupons.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.coupons.application.MemberCouponResult;
import com.horse.global.time.ApiDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberCouponResponse(
	Long couponId,
	String type,
	int totalCount,
	int remainingCount,
	int heldCount,
	int availableCount,
	@Schema(nullable = true) OffsetDateTime firstUsedAt,
	@Schema(nullable = true) OffsetDateTime expiresAt,
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
			ApiDateTime.toSeoulOffset(result.firstUsedAt()),
			ApiDateTime.toSeoulOffset(result.expiresAt()),
			result.freeChangeUsed(),
			result.status());
	}
}
