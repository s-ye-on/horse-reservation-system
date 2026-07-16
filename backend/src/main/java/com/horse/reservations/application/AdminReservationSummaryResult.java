package com.horse.reservations.application;

import java.time.LocalDate;
import java.util.List;

public record AdminReservationSummaryResult(
	LocalDate lessonDateFrom,
	LocalDate lessonDateTo,
	long totalCount,
	List<ReservationStatusCountResult> statusCounts,
	List<AdminReservationDailySummaryResult> dailyCounts
) {
}
