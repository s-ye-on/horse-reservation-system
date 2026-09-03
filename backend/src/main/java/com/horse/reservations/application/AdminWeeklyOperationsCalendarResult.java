package com.horse.reservations.application;

import java.time.LocalDate;
import java.util.List;

public record AdminWeeklyOperationsCalendarResult(
	LocalDate referenceDate,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	List<AdminWeeklyOperationsTimeSlotResult> timeSlots
) {
}
