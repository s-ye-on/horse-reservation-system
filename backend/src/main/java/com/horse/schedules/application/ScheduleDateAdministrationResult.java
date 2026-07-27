package com.horse.schedules.application;

import java.util.List;

public record ScheduleDateAdministrationResult(
	ScheduleDateClosureResult closure,
	ScheduleDateView scheduleDate,
	int initialReservationCount,
	List<ScheduleImpactReservationView> reservations
) {

	public ScheduleDateAdministrationResult {
		reservations = List.copyOf(reservations);
	}
}
