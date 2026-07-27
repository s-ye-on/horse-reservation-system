package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.RecurringHolidayMutationResult;
import com.horse.schedules.application.ScheduleSynchronizationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"holiday", "pendingConfigVersion", "impact", "synchronization"
})
public record RecurringHolidayMutationResponse(
	RecurringHolidayResponse holiday,
	long pendingConfigVersion,
	RecurringHolidayImpactResponse impact,
	ScheduleSynchronizationResponse synchronization
) {

	public static RecurringHolidayMutationResponse from(
		RecurringHolidayMutationResult result,
		ScheduleSynchronizationStatus synchronization
	) {
		return new RecurringHolidayMutationResponse(
			RecurringHolidayResponse.from(result.rule()),
			result.pendingConfigVersion(),
			RecurringHolidayImpactResponse.from(result.impact()),
			ScheduleSynchronizationResponse.from(synchronization));
	}
}
