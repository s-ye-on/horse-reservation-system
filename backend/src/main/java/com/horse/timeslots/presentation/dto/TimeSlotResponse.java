package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

import com.horse.global.time.ApiDateTime;
import com.horse.timeslots.application.TimeSlotResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record TimeSlotResponse(
	Long id,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	int totalCapacity,
	int roundArenaCapacity,
	Map<String, Integer> classCapacities,
	boolean closed,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
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
			ApiDateTime.toSeoulOffset(result.createdAt()),
			ApiDateTime.toSeoulOffset(result.updatedAt()));
	}

}
