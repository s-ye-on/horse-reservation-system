package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminReservationQueryRequest(
	String status,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate lessonDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate lessonDateTo,
	String classType,
	String keyword,
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
