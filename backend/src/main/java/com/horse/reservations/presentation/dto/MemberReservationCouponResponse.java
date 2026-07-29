package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.MemberReservationCouponResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberReservationCouponResponse(
	Long couponId,
	String couponType,
	String status,
	int remainingCount,
	int heldCount,
	int availableCount,
	@Schema(nullable = true) OffsetDateTime expiresAt
) {

	public static MemberReservationCouponResponse from(MemberReservationCouponResult result) {
		return new MemberReservationCouponResponse(
			result.couponId(),
			result.couponType(),
			result.status(),
			result.remainingCount(),
			result.heldCount(),
			result.availableCount(),
			ApiDateTime.toSeoulOffset(result.expiresAt()));
	}
}
