package com.horse.reservations.application;

import java.time.LocalDateTime;

public record PendingPaymentExpiryResult(
	int expiredCount,
	LocalDateTime executedAt
) {
}
