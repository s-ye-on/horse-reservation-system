package com.horse.reservations.application;

import java.time.YearMonth;
import java.util.List;

public record AdminMonthlyRideStatisticsResult(
	YearMonth month,
	MonthlyRideType rideType,
	long totalCompletedRideCount,
	long topCompletedRideCount,
	List<AdminMonthlyRideLeaderResult> leaders
) {
}
