package com.horse.reservations.application;

import java.util.List;

public record MemberReservationPageResult(
	List<MemberReservationResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
}
