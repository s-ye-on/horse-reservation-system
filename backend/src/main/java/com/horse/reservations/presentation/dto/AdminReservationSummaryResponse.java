package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.horse.reservations.application.AdminReservationDailySummaryResult;
import com.horse.reservations.application.AdminReservationSummaryResult;
import com.horse.reservations.application.ReservationStatusCountResult;

public record AdminReservationSummaryResponse(
	LocalDate lessonDateFrom,
	LocalDate lessonDateTo,
	long totalCount,
	List<ReservationStatusCountResponse> statusCounts,
	List<AdminReservationDailySummaryResponse> dailyCounts
) {

	public static AdminReservationSummaryResponse from(AdminReservationSummaryResult result) {
		return new AdminReservationSummaryResponse(
			result.lessonDateFrom(),
			result.lessonDateTo(),
			result.totalCount(),
			result.statusCounts().stream().map(ReservationStatusCountResponse::from).toList(),
			result.dailyCounts().stream().map(AdminReservationDailySummaryResponse::from).toList());
	}

	public record ReservationStatusCountResponse(String status, long count) {

		private static ReservationStatusCountResponse from(ReservationStatusCountResult result) {
			return new ReservationStatusCountResponse(result.status().databaseValue(), result.count());
		}
	}

	public record AdminReservationDailySummaryResponse(
		LocalDate lessonDate,
		long totalCount,
		List<ReservationStatusCountResponse> statusCounts
	) {

		private static AdminReservationDailySummaryResponse from(AdminReservationDailySummaryResult result) {
			return new AdminReservationDailySummaryResponse(
				result.lessonDate(),
				result.totalCount(),
				result.statusCounts().stream().map(ReservationStatusCountResponse::from).toList());
		}
	}
}
