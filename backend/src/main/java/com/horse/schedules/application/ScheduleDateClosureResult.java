package com.horse.schedules.application;

import java.time.LocalDate;

import com.horse.schedules.domain.ScheduleDateStatus;

public record ScheduleDateClosureResult(
	LocalDate scheduleDate,
	ScheduleDateStatus status,
	ScheduleDateStatus resumeStatus,
	int activeReservationCount,
	boolean changed
) {
}
