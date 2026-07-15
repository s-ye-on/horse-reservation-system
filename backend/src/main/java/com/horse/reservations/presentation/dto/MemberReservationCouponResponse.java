package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.MemberReservationCouponResult;

public record MemberReservationCouponResponse(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	int availableCount,
	LocalDateTime expiresAt
) {

	public static MemberReservationCouponResponse from(MemberReservationCouponResult result) {
		return new MemberReservationCouponResponse(
			result.couponId(),
			result.couponType(),
			result.status(),
			result.remainingCount(),
			result.heldCount(),
			result.availableCount(),
			result.expiresAt());
	}
}
