package com.horse.schedules.application;

import java.time.LocalDate;
import java.util.List;

public record ScheduleDateClosureImpact(
	LocalDate scheduleDate,
	List<Long> reservationIds,
	int activeReservationCount
) {

	public ScheduleDateClosureImpact {
		reservationIds = List.copyOf(reservationIds);
	}
}
