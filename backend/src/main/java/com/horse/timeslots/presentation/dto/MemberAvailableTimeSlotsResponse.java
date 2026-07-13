package com.horse.timeslots.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.horse.members.domain.RidingClass;
import com.horse.timeslots.application.MemberAvailableTimeSlotsResult;

public record MemberAvailableTimeSlotsResponse(
	LocalDate date,
	RidingClass classType,
	List<MemberAvailableTimeSlotResponse> timeSlots
) {

	public static MemberAvailableTimeSlotsResponse from(MemberAvailableTimeSlotsResult result) {
		return new MemberAvailableTimeSlotsResponse(
			result.date(),
			result.classType(),
			result.timeSlots().stream()
				.map(MemberAvailableTimeSlotResponse::from)
				.toList());
	}
}
