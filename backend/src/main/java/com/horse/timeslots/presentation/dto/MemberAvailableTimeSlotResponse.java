package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.timeslots.application.MemberAvailableTimeSlotResult;
import com.horse.timeslots.application.TimeSlotAvailabilityReason;

public record MemberAvailableTimeSlotResponse(
	Long timeSlotId,
	LocalDate lessonDate,
	LocalTime startTime,
	boolean closed,
	boolean reservable,
	int remainingCapacity,
	TimeSlotAvailabilityReason unavailableReason
) {

	public static MemberAvailableTimeSlotResponse from(MemberAvailableTimeSlotResult result) {
		return new MemberAvailableTimeSlotResponse(
			result.timeSlotId(),
			result.lessonDate(),
			result.startTime(),
			result.closed(),
			result.reservable(),
			result.remainingCapacity(),
			result.unavailableReason());
	}
}
