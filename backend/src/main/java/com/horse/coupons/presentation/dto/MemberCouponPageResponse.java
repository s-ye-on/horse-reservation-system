package com.horse.coupons.presentation.dto;

import java.util.List;

import com.horse.coupons.application.MemberCouponPageResult;

public record MemberCouponPageResponse(
	List<MemberCouponResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static MemberCouponPageResponse from(MemberCouponPageResult result) {
		return new MemberCouponPageResponse(
			result.content().stream().map(MemberCouponResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
