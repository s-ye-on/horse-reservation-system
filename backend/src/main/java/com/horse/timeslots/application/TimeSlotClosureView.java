package com.horse.timeslots.application;

import java.time.LocalDateTime;
import java.util.List;

import com.horse.timeslots.domain.TimeSlotClosureStatus;

public record TimeSlotClosureView(
	long timeSlotId,
	boolean adminClosed,
	boolean closed,
	TimeSlotClosureStatus status,
	String reason,
	LocalDateTime startedAt,
	LocalDateTime completedAt,
	LocalDateTime withdrawnAt,
	long version,
	int totalCount,
	int resolvedCount,
	int unresolvedCount,
	List<TimeSlotClosureImpactView> impacts
) {

	public TimeSlotClosureView {
		impacts = List.copyOf(impacts);
	}
}
