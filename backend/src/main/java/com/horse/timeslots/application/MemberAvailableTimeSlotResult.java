package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalTime;

public record MemberAvailableTimeSlotResult(
	Long timeSlotId,
	LocalDate lessonDate,
	LocalTime startTime,
	boolean closed,
	boolean reservable,
	int remainingCapacity,
	TimeSlotAvailabilityReason unavailableReason
) {
}
