package com.horse.coupons.application;

import java.time.LocalDateTime;

import com.horse.coupons.domain.CouponUsageLog;

public record MemberCouponUsageResult(
	Long usageLogId,
	Long couponId,
	Long reservationId,
	Long memberId,
	Long couponOwnerMemberId,
	Long familyGroupId,
	String action,
	int countDelta,
	LocalDateTime occurredAt,
	String actorType
) {

	public static MemberCouponUsageResult from(CouponUsageLog usageLog) {
		return new MemberCouponUsageResult(
			usageLog.getId(),
			usageLog.getCouponId(),
			usageLog.getReservationId(),
			usageLog.getMemberId(),
			usageLog.getCouponOwnerMemberId(),
			usageLog.getFamilyGroupId(),
			usageLog.getAction().value(),
			usageLog.getCountDelta(),
			usageLog.getOccurredAt(),
			usageLog.getActorType().value());
	}
}
