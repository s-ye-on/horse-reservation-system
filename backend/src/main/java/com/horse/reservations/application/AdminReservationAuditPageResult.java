package com.horse.reservations.application;

import java.util.List;

public record AdminReservationAuditPageResult(
	List<AdminReservationAuditResult> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
