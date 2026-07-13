package com.horse.timeslots.presentation.dto;

import java.util.Map;

public record TimeSlotCapacityUpdateRequest(
	Integer totalCapacity,
	Integer roundArenaCapacity,
	Map<String, Integer> classCapacities
) {
}
