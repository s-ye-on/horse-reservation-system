package com.horse.timeslots.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.exception.TimeSlotException;

public final class TimeSlotOperationTimePolicy {

	private TimeSlotOperationTimePolicy() {
	}

	public static void ensureNotStarted(
		LocalDate lessonDate,
		LocalTime startTime,
		LocalDateTime requestedAt
	) {
		if (!requestedAt.isBefore(LocalDateTime.of(lessonDate, startTime))) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_LESSON_ALREADY_STARTED);
		}
	}

}
