package com.horse.coupons.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.coupons.application.MemberCouponUsageResult;
import com.horse.global.time.ApiDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberCouponUsageResponse(
	Long usageLogId,
	Long couponId,
	@Schema(nullable = true) Long reservationId,
	Long memberId,
	Long couponOwnerMemberId,
	@Schema(nullable = true) Long familyGroupId,
	String action,
	int countDelta,
	OffsetDateTime occurredAt,
	String actorType
) {

	public static MemberCouponUsageResponse from(MemberCouponUsageResult result) {
		return new MemberCouponUsageResponse(
			result.usageLogId(),
			result.couponId(),
			result.reservationId(),
			result.memberId(),
			result.couponOwnerMemberId(),
			result.familyGroupId(),
			result.action(),
			result.countDelta(),
			ApiDateTime.toSeoulOffset(result.occurredAt()),
			result.actorType());
	}
}
