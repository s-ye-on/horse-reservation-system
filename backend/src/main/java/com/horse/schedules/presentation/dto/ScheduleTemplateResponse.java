package com.horse.schedules.presentation.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

import com.horse.schedules.application.RegularScheduleTemplateView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"templateId", "dayOfWeek", "startTime", "endTime", "totalCapacity",
	"roundArenaCapacity", "classCapacities", "active", "version"
})
public record ScheduleTemplateResponse(
	long templateId,
	DayOfWeek dayOfWeek,
	@Schema(type = "string", format = "time") LocalTime startTime,
	@Schema(type = "string", format = "time") LocalTime endTime,
	int totalCapacity,
	int roundArenaCapacity,
	Map<String, Integer> classCapacities,
	boolean active,
	long version
) {

	public static ScheduleTemplateResponse from(RegularScheduleTemplateView view) {
		return new ScheduleTemplateResponse(
			view.id(),
			view.dayOfWeek(),
			view.startTime(),
			view.endTime(),
			view.totalCapacity(),
			view.roundArenaCapacity(),
			view.classCapacities(),
			view.active(),
			view.version());
	}
}
