package com.horse.reservations.presentation.dto;

import java.util.List;

import com.horse.reservations.application.MemberReservationPageResult;

public record MemberReservationPageResponse(
	List<MemberReservationResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {

	public static MemberReservationPageResponse from(MemberReservationPageResult result) {
		return new MemberReservationPageResponse(
			result.content().stream().map(MemberReservationResponse::from).toList(),
			result.page(),
			result.size(),
			result.totalElements(),
			result.totalPages(),
			result.hasNext());
	}
}
