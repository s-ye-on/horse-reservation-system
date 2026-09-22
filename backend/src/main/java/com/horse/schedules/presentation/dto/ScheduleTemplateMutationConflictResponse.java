package com.horse.schedules.presentation.dto;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = {
	ScheduleTemplateGeneralConflictErrorResponse.class,
	ScheduleTemplateCapacityConflictErrorResponse.class
}, discriminatorProperty = "code", discriminatorMapping = {
	@DiscriminatorMapping(
		value = "SCHEDULE_CONFIG_VERSION_CONFLICT",
		schema = ScheduleTemplateGeneralConflictErrorResponse.class),
	@DiscriminatorMapping(
		value = "SCHEDULE_TEMPLATE_ALREADY_EXISTS",
		schema = ScheduleTemplateGeneralConflictErrorResponse.class),
	@DiscriminatorMapping(
		value = "TIMESLOT_CAPACITY_BELOW_OCCUPANCY",
		schema = ScheduleTemplateCapacityConflictErrorResponse.class)
})
public record ScheduleTemplateMutationConflictResponse() {
}
