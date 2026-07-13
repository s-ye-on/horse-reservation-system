package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import com.horse.timeslots.application.TimeSlotResult;

public record TimeSlotResponse(
	Long id,
	LocalDate lessonDate,
	LocalTime startTime,
	int totalCapacity,
	int roundArenaCapacity,
	Map<String, Integer> classCapacities,
	boolean closed,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static TimeSlotResponse from(TimeSlotResult result) {
		return new TimeSlotResponse(
			result.id(),
			result.lessonDate(),
			result.startTime(),
			result.totalCapacity(),
			result.roundArenaCapacity(),
			result.classCapacities(),
			result.closed(),
			result.createdAt(),
			result.updatedAt());
	}

}
