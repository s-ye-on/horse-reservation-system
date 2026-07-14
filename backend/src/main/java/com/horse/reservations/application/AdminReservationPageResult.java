package com.horse.reservations.application;

import java.util.List;

public record AdminReservationPageResult(
	List<AdminReservationResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
