package com.horse.coupons.presentation.dto;

import java.time.LocalDateTime;

import com.horse.coupons.application.MemberCouponUsageResult;

public record MemberCouponUsageResponse(
	Long usageLogId,
	Long couponId,
	Long reservationId,
	String action,
	int countDelta,
	LocalDateTime occurredAt,
	String actorType
) {

	public static MemberCouponUsageResponse from(MemberCouponUsageResult result) {
		return new MemberCouponUsageResponse(
			result.usageLogId(),
			result.couponId(),
			result.reservationId(),
			result.action(),
			result.countDelta(),
			result.occurredAt(),
			result.actorType());
	}
}
