package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import com.horse.reservations.application.AdminWeeklyOperationsCalendarResult;
import com.horse.reservations.application.AdminWeeklyOperationsReservationResult;
import com.horse.reservations.application.AdminWeeklyOperationsTimeSlotResult;

public record AdminWeeklyOperationsCalendarResponse(
	LocalDate referenceDate,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	List<TimeSlotResponse> timeSlots
) {

	public static AdminWeeklyOperationsCalendarResponse from(AdminWeeklyOperationsCalendarResult result) {
		return new AdminWeeklyOperationsCalendarResponse(
			result.referenceDate(),
			result.weekStartDate(),
			result.weekEndDate(),
			result.timeSlots().stream().map(TimeSlotResponse::from).toList());
	}

	public record TimeSlotResponse(
		Long timeSlotId,
		LocalDate lessonDate,
		@Schema(type = "string", format = "time") LocalTime startTime,
		@Schema(type = "string", format = "time") LocalTime endTime,
		int totalCapacity,
		int roundArenaCapacity,
		Map<String, Integer> classCapacities,
		boolean closed,
		List<ReservationResponse> reservations
	) {

		private static TimeSlotResponse from(AdminWeeklyOperationsTimeSlotResult result) {
			return new TimeSlotResponse(
				result.timeSlotId(),
				result.lessonDate(),
				result.startTime(),
				result.endTime(),
				result.totalCapacity(),
				result.roundArenaCapacity(),
				result.classCapacities(),
				result.closed(),
				result.reservations().stream().map(ReservationResponse::from).toList());
		}
	}

	public record ReservationResponse(
		Long reservationId,
		Long memberId,
		String memberName,
		String ridingClass,
		String status
	) {

		private static ReservationResponse from(AdminWeeklyOperationsReservationResult result) {
			return new ReservationResponse(
				result.reservationId(),
				result.memberId(),
				result.memberName(),
				result.ridingClass().name(),
				result.status().databaseValue());
		}
	}

}
