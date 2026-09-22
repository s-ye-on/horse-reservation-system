package com.horse.schedules.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.horse.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"code", "message", "status", "timestamp", "path", "fieldErrors", "details"
})
public record ScheduleTemplateCapacityConflictErrorResponse(
	@Schema(allowableValues = "TIMESLOT_CAPACITY_BELOW_OCCUPANCY") String code,
	String message,
	int status,
	OffsetDateTime timestamp,
	String path,
	List<ErrorResponse.FieldError> fieldErrors,
	ScheduleTemplateCapacityConflictDetails details
) {
}
