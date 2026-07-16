package com.horse.coupons.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Positive;

public record AdminCouponUsageExportRequest(
	String keyword,
	@Positive
	Long couponId,
	@Positive
	Long reservationId,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateTo
) {
}
