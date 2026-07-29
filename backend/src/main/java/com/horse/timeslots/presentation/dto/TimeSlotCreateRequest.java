package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

public record TimeSlotCreateRequest(
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	Integer totalCapacity,
	Integer roundArenaCapacity,
	Map<String, Integer> classCapacities
) {
}
