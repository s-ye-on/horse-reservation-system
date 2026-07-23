package com.horse.schedules.application;

import java.time.LocalDate;

public record ScheduleDateHorizonResult(
	LocalDate startDate,
	LocalDate endDate,
	int insertedCount,
	long appliedConfigVersion
) {
}
