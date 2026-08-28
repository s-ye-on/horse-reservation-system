package com.horse.reservations.presentation.dto;

import java.time.YearMonth;
import java.util.List;

import com.horse.reservations.application.AdminMonthlyRideLeaderResult;
import com.horse.reservations.application.AdminMonthlyRideStatisticsResult;

public record AdminMonthlyRideStatisticsResponse(
	YearMonth month,
	String rideType,
	long totalCompletedRideCount,
	long topCompletedRideCount,
	List<AdminMonthlyRideLeaderResponse> leaders
) {

	public static AdminMonthlyRideStatisticsResponse from(AdminMonthlyRideStatisticsResult result) {
		return new AdminMonthlyRideStatisticsResponse(
			result.month(),
			result.rideType().name(),
			result.totalCompletedRideCount(),
			result.topCompletedRideCount(),
			result.leaders().stream().map(AdminMonthlyRideLeaderResponse::from).toList());
	}

	public record AdminMonthlyRideLeaderResponse(
		Long memberId,
		String memberName,
		long completedRideCount
	) {

		private static AdminMonthlyRideLeaderResponse from(AdminMonthlyRideLeaderResult result) {
			return new AdminMonthlyRideLeaderResponse(
				result.memberId(),
				result.memberName(),
				result.completedRideCount());
		}
	}
}
