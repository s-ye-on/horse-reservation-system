package com.horse.schedules.application;

import java.util.List;

public record ScheduleTemplateFutureReservationsResult(
	long templateId,
	List<ScheduleTemplateFutureReservationView> reservations
) {

	public ScheduleTemplateFutureReservationsResult {
		reservations = List.copyOf(reservations);
	}

	public int reservationCount() {
		return reservations.size();
	}
}
