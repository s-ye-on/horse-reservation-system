package com.horse.coupons.application;

import java.util.List;

public record MemberCouponPageResult(
	List<MemberCouponResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
