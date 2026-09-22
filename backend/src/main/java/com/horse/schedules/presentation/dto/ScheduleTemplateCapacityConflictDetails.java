package com.horse.schedules.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import com.horse.members.domain.RidingClass;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"lessonDate", "startTime", "totalOccupied", "roundArenaOccupied", "classOccupied",
	"requestedTotalCapacity", "requestedRoundArenaCapacity", "requestedClassCapacities"
})
public record ScheduleTemplateCapacityConflictDetails(
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	int totalOccupied,
	int roundArenaOccupied,
	Map<RidingClass, Integer> classOccupied,
	int requestedTotalCapacity,
	int requestedRoundArenaCapacity,
	Map<RidingClass, Integer> requestedClassCapacities
) {
}
