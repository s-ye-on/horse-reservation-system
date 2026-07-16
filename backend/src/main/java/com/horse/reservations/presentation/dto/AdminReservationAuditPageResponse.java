package com.horse.reservations.presentation.dto;

import java.util.List;

import com.horse.reservations.application.AdminReservationAuditPageResult;

public record AdminReservationAuditPageResponse(
	List<AdminReservationAuditResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static AdminReservationAuditPageResponse from(AdminReservationAuditPageResult result) {
		return new AdminReservationAuditPageResponse(
			result.content().stream().map(AdminReservationAuditResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages());
	}
}
