package com.horse.schedules.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

public record RegularScheduleTemplateCommand(
	DayOfWeek dayOfWeek,
	LocalTime startTime,
	LocalTime endTime,
	Integer totalCapacity,
	Integer roundArenaCapacity,
	Map<String, Integer> classCapacities,
	long expectedConfigVersion,
	String actorAuthSubject,
	String reason
) {
}
