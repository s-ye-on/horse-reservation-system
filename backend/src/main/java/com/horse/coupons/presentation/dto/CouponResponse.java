package com.horse.coupons.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.coupons.application.CouponRegistrationResult;
import com.horse.global.time.ApiDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record CouponResponse(
	Long id,
	Long memberId,
	String type,
	int totalCount,
	int remainingCount,
	int heldCount,
	@Schema(nullable = true) OffsetDateTime firstUsedAt,
	@Schema(nullable = true) OffsetDateTime expiresAt,
	boolean freeChangeUsed,
	String status,
	String createdBy,
	OffsetDateTime createdAt
) {

	public static CouponResponse from(CouponRegistrationResult result) {
		return new CouponResponse(
			result.id(),
			result.memberId(),
			result.type(),
			result.totalCount(),
			result.remainingCount(),
			result.heldCount(),
			ApiDateTime.toSeoulOffset(result.firstUsedAt()),
			ApiDateTime.toSeoulOffset(result.expiresAt()),
			result.freeChangeUsed(),
			result.status(),
			result.createdBy(),
			ApiDateTime.toSeoulOffset(result.createdAt()));
	}
}
