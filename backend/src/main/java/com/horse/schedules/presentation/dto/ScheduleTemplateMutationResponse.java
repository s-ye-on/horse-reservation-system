package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.ScheduleTemplateMutationResult;
import com.horse.schedules.application.ScheduleSynchronizationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"template", "pendingConfigVersion", "impact", "synchronization"
})
public record ScheduleTemplateMutationResponse(
	ScheduleTemplateResponse template,
	long pendingConfigVersion,
	ScheduleTemplateImpactResponse impact,
	ScheduleSynchronizationResponse synchronization
) {

	public static ScheduleTemplateMutationResponse from(
		ScheduleTemplateMutationResult result,
		ScheduleSynchronizationStatus synchronization
	) {
		return new ScheduleTemplateMutationResponse(
			ScheduleTemplateResponse.from(result.template()),
			result.pendingConfigVersion(),
			ScheduleTemplateImpactResponse.from(result.impact()),
			ScheduleSynchronizationResponse.from(synchronization));
	}
}
