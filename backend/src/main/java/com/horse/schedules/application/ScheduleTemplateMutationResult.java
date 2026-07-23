package com.horse.schedules.application;

public record ScheduleTemplateMutationResult(
	RegularScheduleTemplateView template,
	long pendingConfigVersion,
	ScheduleTemplateImpactPreview impact
) {
}
