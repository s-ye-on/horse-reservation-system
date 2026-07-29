package com.horse.schedules.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.horse.schedules.application.ScheduleDateClosureResult;
import com.horse.schedules.application.ScheduleDateView;
import com.horse.schedules.application.ScheduleImpactReservationView;
import com.horse.schedules.domain.ScheduleDateStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"scheduleDate", "status", "version", "initialReservationCount",
	"activeReservationCount", "resolvedReservationCount", "remainingReservationCount",
	"progressPercent", "reservations", "changed"
})
public record ScheduleDateClosureImpactResponse(
	LocalDate scheduleDate,
	ScheduleDateStatus status,
	@Schema(nullable = true) ScheduleDateStatus resumeStatus,
	long version,
	int initialReservationCount,
	int activeReservationCount,
	int resolvedReservationCount,
	int remainingReservationCount,
	int progressPercent,
	List<ScheduleImpactReservationResponse> reservations,
	boolean changed
) {

	public ScheduleDateClosureImpactResponse {
		reservations = List.copyOf(reservations);
	}

	public static ScheduleDateClosureImpactResponse from(
		ScheduleDateClosureResult result,
		ScheduleDateView scheduleDate,
		List<ScheduleImpactReservationView> reservations,
		int initialReservationCount
	) {
		final List<ScheduleImpactReservationResponse> responses = reservations.stream()
			.map(ScheduleImpactReservationResponse::from)
			.toList();
		return new ScheduleDateClosureImpactResponse(
			result.scheduleDate(),
			result.status(),
			result.resumeStatus(),
			scheduleDate.version(),
			initialReservationCount,
			result.activeReservationCount(),
			resolvedCount(initialReservationCount, responses.size()),
			responses.size(),
			progress(initialReservationCount, responses.size()),
			responses,
			result.changed());
	}

	public static ScheduleDateClosureImpactResponse from(
		ScheduleDateView scheduleDate,
		List<ScheduleImpactReservationView> reservations,
		int initialReservationCount
	) {
		final List<ScheduleImpactReservationResponse> responses = reservations.stream()
			.map(ScheduleImpactReservationResponse::from)
			.toList();
		return new ScheduleDateClosureImpactResponse(
			scheduleDate.scheduleDate(),
			scheduleDate.status(),
			scheduleDate.resumeStatus(),
			scheduleDate.version(),
			initialReservationCount,
			responses.size(),
			resolvedCount(initialReservationCount, responses.size()),
			responses.size(),
			progress(initialReservationCount, responses.size()),
			responses,
			false);
	}

	private static int resolvedCount(int initialCount, int remainingCount) {
		return Math.max(initialCount - remainingCount, 0);
	}

	private static int progress(int initialCount, int remainingCount) {
		return initialCount == 0
			? 100
			: resolvedCount(initialCount, remainingCount) * 100 / initialCount;
	}
}
