package com.horse.schedules.presentation.dto;

import java.time.LocalDate;

import com.horse.schedules.application.ScheduleDateView;
import com.horse.schedules.domain.ScheduleDateStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"scheduleDate", "status", "appliedConfigVersion", "version"
})
public record ScheduleDateResponse(
	LocalDate scheduleDate,
	ScheduleDateStatus status,
	ScheduleDateStatus resumeStatus,
	String reason,
	long appliedConfigVersion,
	long version
) {

	public static ScheduleDateResponse from(ScheduleDateView view) {
		return new ScheduleDateResponse(
			view.scheduleDate(),
			view.status(),
			view.resumeStatus(),
			view.reason(),
			view.appliedConfigVersion(),
			view.version());
	}
}
