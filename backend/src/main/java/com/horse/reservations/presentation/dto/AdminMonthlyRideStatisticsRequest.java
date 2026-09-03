package com.horse.reservations.presentation.dto;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminMonthlyRideStatisticsRequest(
	@Schema(
		type = "string",
		pattern = "^\\d{4}-(0[1-9]|1[0-2])$",
		example = "2026-08",
		description = "조회 월. 생략하면 Asia/Seoul 현재 월")
	@DateTimeFormat(pattern = "yyyy-MM")
	YearMonth month,
	@Schema(
		allowableValues = {"ALL", "GENERAL", "DRESSAGE", "JUMPING"},
		defaultValue = "ALL")
	String rideType
) {
}
