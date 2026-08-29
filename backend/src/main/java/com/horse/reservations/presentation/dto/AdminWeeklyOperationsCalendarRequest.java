package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

public record AdminWeeklyOperationsCalendarRequest(
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate referenceDate
) {
}
