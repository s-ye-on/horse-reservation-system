package com.horse.timeslots.application;

import java.time.LocalDate;
import java.util.List;

import com.horse.members.domain.RidingClass;

public record MemberAvailableTimeSlotsResult(
	LocalDate date,
	RidingClass classType,
	List<MemberAvailableTimeSlotResult> timeSlots
) {
}
