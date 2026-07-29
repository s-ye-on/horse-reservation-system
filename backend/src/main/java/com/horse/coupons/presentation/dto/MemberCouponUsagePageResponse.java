package com.horse.coupons.presentation.dto;

import java.util.List;

import com.horse.coupons.application.MemberCouponUsagePageResult;

public record MemberCouponUsagePageResponse(
	List<MemberCouponUsageResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static MemberCouponUsagePageResponse from(MemberCouponUsagePageResult result) {
		return new MemberCouponUsagePageResponse(
			result.content().stream().map(MemberCouponUsageResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
