package com.horse.coupons.presentation.dto;

public record CouponRegistrationRequest(
	String type,
	Integer totalCount
) {
}
