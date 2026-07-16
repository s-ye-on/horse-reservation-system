package com.horse.reservations.application;

import java.time.LocalDate;
import java.util.List;

public record AdminReservationDailySummaryResult(
	LocalDate lessonDate,
	long totalCount,
	List<ReservationStatusCountResult> statusCounts
) {
}
