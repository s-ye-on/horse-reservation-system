package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record BulkReservationAttendanceRequest(
	@NotNull
	LocalDate lessonDate,
	@NotNull
	@Schema(type = "string", format = "time")
	LocalTime startTime,
	@NotEmpty
	@Size(max = MAX_ITEMS)
	List<@Valid BulkReservationAttendanceItemRequest> items
) {

	private static final int MAX_ITEMS = 8;
}
