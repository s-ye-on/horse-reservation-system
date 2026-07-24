package com.horse.schedules.application;

import java.time.LocalDate;

record ScheduleSynchronizationPreparation(
	long targetVersion,
	LocalDate horizonStart,
	LocalDate horizonEnd,
	boolean alreadyCompleted
) {
}
