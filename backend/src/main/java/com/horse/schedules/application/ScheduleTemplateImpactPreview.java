package com.horse.schedules.application;

public record ScheduleTemplateImpactPreview(
	long affectedDateCount,
	long existingTimeSlotCount,
	long activeReservationCount
) {
}
