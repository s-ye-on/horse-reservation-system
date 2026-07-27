package com.horse.timeslots.presentation.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.horse.timeslots.application.TimeSlotClosureView;
import com.horse.timeslots.domain.TimeSlotClosureStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"timeSlotId", "adminClosed", "closed", "status", "reason", "startedAt",
	"version", "totalCount", "resolvedCount", "unresolvedCount", "progressPercent", "impacts"
})
public record TimeSlotClosureResponse(
	long timeSlotId,
	boolean adminClosed,
	boolean closed,
	TimeSlotClosureStatus status,
	String reason,
	OffsetDateTime startedAt,
	OffsetDateTime completedAt,
	OffsetDateTime withdrawnAt,
	long version,
	int totalCount,
	int resolvedCount,
	int unresolvedCount,
	int progressPercent,
	List<TimeSlotClosureImpactResponse> impacts
) {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	public TimeSlotClosureResponse {
		impacts = List.copyOf(impacts);
	}

	public static TimeSlotClosureResponse from(TimeSlotClosureView view) {
		final int progress = view.totalCount() == 0
			? 100
			: view.resolvedCount() * 100 / view.totalCount();
		return new TimeSlotClosureResponse(
			view.timeSlotId(),
			view.adminClosed(),
			view.closed(),
			view.status(),
			view.reason(),
			toOffset(view.startedAt()),
			toOffset(view.completedAt()),
			toOffset(view.withdrawnAt()),
			view.version(),
			view.totalCount(),
			view.resolvedCount(),
			view.unresolvedCount(),
			progress,
			view.impacts().stream()
				.map(TimeSlotClosureImpactResponse::from)
				.toList());
	}

	private static OffsetDateTime toOffset(LocalDateTime value) {
		return value == null ? null : value.atZone(SEOUL_ZONE).toOffsetDateTime();
	}
}
