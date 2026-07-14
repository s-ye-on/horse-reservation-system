package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.PendingPaymentExpiryResult;

public record PendingPaymentExpiryResponse(
	int expiredCount,
	LocalDateTime executedAt
) {

	public static PendingPaymentExpiryResponse from(PendingPaymentExpiryResult result) {
		return new PendingPaymentExpiryResponse(result.expiredCount(), result.executedAt());
	}
}
