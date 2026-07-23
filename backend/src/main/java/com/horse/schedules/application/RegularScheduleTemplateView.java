package com.horse.schedules.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

import com.horse.schedules.domain.RegularScheduleTemplate;

public record RegularScheduleTemplateView(
	long id,
	DayOfWeek dayOfWeek,
	LocalTime startTime,
	LocalTime endTime,
	int totalCapacity,
	int roundArenaCapacity,
	Map<String, Integer> classCapacities,
	boolean active,
	long version
) {

	public static RegularScheduleTemplateView from(RegularScheduleTemplate template) {
		return new RegularScheduleTemplateView(
			template.getId(),
			template.getDayOfWeek(),
			template.getStartTime(),
			template.getEndTime(),
			template.getTotalCapacity(),
			template.getRoundArenaCapacity(),
			template.getClassCapacities(),
			template.isActive(),
			template.getVersion());
	}
}
