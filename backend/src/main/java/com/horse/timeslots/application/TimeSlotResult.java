package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import com.horse.timeslots.domain.TimeSlotCapacity;

public record TimeSlotResult(
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

	public static TimeSlotResult from(TimeSlotCapacity timeSlot) {
		return new TimeSlotResult(
			timeSlot.getId(),
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			timeSlot.getTotalCapacity(),
			timeSlot.getRoundArenaCapacity(),
			timeSlot.getClassCapacities(),
			timeSlot.isClosed(),
			timeSlot.getCreatedAt(),
			timeSlot.getUpdatedAt());
	}

}
