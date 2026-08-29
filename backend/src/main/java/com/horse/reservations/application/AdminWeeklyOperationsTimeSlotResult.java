package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.horse.timeslots.domain.TimeSlotCapacity;

public record AdminWeeklyOperationsTimeSlotResult(
	Long timeSlotId,
	LocalDate lessonDate,
	LocalTime startTime,
	LocalTime endTime,
	int totalCapacity,
	int roundArenaCapacity,
	Map<String, Integer> classCapacities,
	boolean closed,
	List<AdminWeeklyOperationsReservationResult> reservations
) {

	public static AdminWeeklyOperationsTimeSlotResult from(
		TimeSlotCapacity timeSlot,
		List<AdminWeeklyOperationsReservationResult> reservations
	) {
		return new AdminWeeklyOperationsTimeSlotResult(
			timeSlot.getId(),
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			timeSlot.getEndTime(),
			timeSlot.getTotalCapacity(),
			timeSlot.getRoundArenaCapacity(),
			timeSlot.getClassCapacities(),
			timeSlot.isClosed(),
			reservations);
	}

}
