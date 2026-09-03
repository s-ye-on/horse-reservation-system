package com.horse.reservations.presentation.dto;

import java.time.YearMonth;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.horse.reservations.application.AdminMonthlyRideLeaderResult;
import com.horse.reservations.application.AdminMonthlyRideStatisticsResult;

public record AdminMonthlyRideStatisticsResponse(
	@Schema(
		type = "string",
		pattern = "^\\d{4}-(0[1-9]|1[0-2])$",
		example = "2026-08")
	YearMonth month,
	@Schema(allowableValues = {"ALL", "GENERAL", "DRESSAGE", "JUMPING"})
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

	@Schema(name = "AdminMonthlyRideLeaderResponse")
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
