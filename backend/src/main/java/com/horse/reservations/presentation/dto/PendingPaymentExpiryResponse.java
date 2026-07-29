package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.PendingPaymentExpiryResult;

public record PendingPaymentExpiryResponse(
	int expiredCount,
	OffsetDateTime executedAt
) {

	public static PendingPaymentExpiryResponse from(PendingPaymentExpiryResult result) {
		return new PendingPaymentExpiryResponse(
			result.expiredCount(),
			ApiDateTime.toSeoulOffset(result.executedAt()));
	}
}
