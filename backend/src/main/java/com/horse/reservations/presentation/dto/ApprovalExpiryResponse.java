package com.horse.reservations.presentation.dto;

import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ApprovalExpiryResult;

public record ApprovalExpiryResponse(
	int expiredCount,
	OffsetDateTime executedAt
) {

	public static ApprovalExpiryResponse from(ApprovalExpiryResult result) {
		return new ApprovalExpiryResponse(
			result.expiredCount(),
			ApiDateTime.toSeoulOffset(result.executedAt()));
	}
}
