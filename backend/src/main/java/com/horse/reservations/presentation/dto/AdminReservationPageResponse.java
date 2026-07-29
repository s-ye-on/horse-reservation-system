package com.horse.reservations.presentation.dto;

import java.util.List;

import com.horse.reservations.application.AdminReservationPageResult;

public record AdminReservationPageResponse(
	List<AdminReservationResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static AdminReservationPageResponse from(AdminReservationPageResult result) {
		return new AdminReservationPageResponse(
			result.content().stream().map(AdminReservationResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
