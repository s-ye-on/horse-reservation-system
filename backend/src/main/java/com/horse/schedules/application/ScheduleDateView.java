package com.horse.schedules.application;

import java.time.LocalDate;

import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.ScheduleDateStatus;

public record ScheduleDateView(
	LocalDate scheduleDate,
	ScheduleDateStatus status,
	ScheduleDateStatus resumeStatus,
	String reason,
	long appliedConfigVersion,
	long version
) {

	public static ScheduleDateView from(ScheduleDate scheduleDate) {
		return new ScheduleDateView(
			scheduleDate.getScheduleDate(),
			scheduleDate.getStatus(),
			scheduleDate.getResumeStatus(),
			scheduleDate.getReason(),
			scheduleDate.getAppliedConfigVersion(),
			scheduleDate.getVersion());
	}
}
