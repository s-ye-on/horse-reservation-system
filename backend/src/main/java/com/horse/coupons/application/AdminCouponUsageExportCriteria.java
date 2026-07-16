package com.horse.coupons.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

public record AdminCouponUsageExportCriteria(
	String keyword,
	Long couponId,
	Long reservationId,
	LocalDateTime occurredAtFrom,
	LocalDateTime occurredAtTo
) {

	public static AdminCouponUsageExportCriteria create(
		String keyword,
		Long couponId,
		Long reservationId,
		LocalDate occurredDateFrom,
		LocalDate occurredDateTo
	) {
		if (occurredDateFrom != null && occurredDateTo != null
			&& occurredDateFrom.isAfter(occurredDateTo)) {
			throw new CouponException(ExceptionCode.COUPON_INVALID_EXPORT_DATE_RANGE);
		}
		return new AdminCouponUsageExportCriteria(
			keyword == null || keyword.isBlank() ? null : keyword.strip(),
			couponId,
			reservationId,
			occurredDateFrom == null ? null : occurredDateFrom.atStartOfDay(),
			occurredDateTo == null ? null : occurredDateTo.atTime(LocalTime.MAX));
	}
}
