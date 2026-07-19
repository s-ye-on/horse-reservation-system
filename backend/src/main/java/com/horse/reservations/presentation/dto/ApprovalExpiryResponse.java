package com.horse.reservations.presentation.dto;

import java.time.LocalDateTime;

import com.horse.reservations.application.ApprovalExpiryResult;

public record ApprovalExpiryResponse(
	int expiredCount,
	LocalDateTime executedAt
) {

	public static ApprovalExpiryResponse from(ApprovalExpiryResult result) {
		return new ApprovalExpiryResponse(result.expiredCount(), result.executedAt());
	}
}
