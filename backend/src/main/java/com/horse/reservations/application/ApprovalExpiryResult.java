package com.horse.reservations.application;

import java.time.LocalDateTime;

public record ApprovalExpiryResult(
	int expiredCount,
	LocalDateTime executedAt
) {
}
