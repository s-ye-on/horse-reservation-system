package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.ScheduleTemplateImpactPreview;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"affectedDateCount", "existingTimeSlotCount", "activeReservationCount"
})
public record ScheduleTemplateImpactResponse(
	long affectedDateCount,
	long existingTimeSlotCount,
	long activeReservationCount
) {

	public static ScheduleTemplateImpactResponse from(ScheduleTemplateImpactPreview preview) {
		return new ScheduleTemplateImpactResponse(
			preview.affectedDateCount(),
			preview.existingTimeSlotCount(),
			preview.activeReservationCount());
	}
}
