package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminWeeklyOperationsCalendarRequest(
	@Schema(
		type = "string",
		implementation = String.class,
		pattern = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
		example = "2026-08-31",
		description = "조회 기준일. 생략하면 Asia/Seoul 현재 날짜")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate referenceDate
) {
}
