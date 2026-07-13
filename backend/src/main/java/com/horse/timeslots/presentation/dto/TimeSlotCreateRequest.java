package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record TimeSlotCreateRequest(
	LocalDate lessonDate,
	LocalTime startTime,
	Integer totalCapacity,
	Integer roundArenaCapacity,
	Map<String, Integer> classCapacities
) {
}
