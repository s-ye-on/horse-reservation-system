package com.horse.reservations.presentation.dto;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;

public record AdminMonthlyRideStatisticsRequest(
	@DateTimeFormat(pattern = "yyyy-MM")
	YearMonth month,
	String rideType
) {
}
