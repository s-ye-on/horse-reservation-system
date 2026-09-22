package com.horse.schedules.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.horse.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"code", "message", "status", "timestamp", "path", "fieldErrors", "details"
})
public record ScheduleTemplateGeneralConflictErrorResponse(
	@Schema(allowableValues = {
		"SCHEDULE_CONFIG_VERSION_CONFLICT", "SCHEDULE_TEMPLATE_ALREADY_EXISTS"
	}) String code,
	String message,
	int status,
	OffsetDateTime timestamp,
	String path,
	List<ErrorResponse.FieldError> fieldErrors,
	@Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
	Map<String, Object> details
) {
}
