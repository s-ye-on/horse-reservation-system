package com.horse.schedules.presentation.dto;

import java.util.List;

import com.horse.schedules.application.ScheduleTemplateFutureReservationsResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"templateId", "reservationCount", "reservations"})
public record ScheduleTemplateFutureReservationsResponse(
	long templateId,
	int reservationCount,
	List<ScheduleTemplateFutureReservationResponse> reservations
) {

	public ScheduleTemplateFutureReservationsResponse {
		reservations = List.copyOf(reservations);
	}

	public static ScheduleTemplateFutureReservationsResponse from(
		ScheduleTemplateFutureReservationsResult result
	) {
		return new ScheduleTemplateFutureReservationsResponse(
			result.templateId(),
			result.reservationCount(),
			result.reservations().stream()
				.map(ScheduleTemplateFutureReservationResponse::from)
				.toList());
	}
}
