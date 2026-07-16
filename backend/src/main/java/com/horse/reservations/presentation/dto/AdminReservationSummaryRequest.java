package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

public record AdminReservationSummaryRequest(
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate lessonDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate lessonDateTo
) {
}
