package com.horse.timeslots.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeSlotClosureAdministrationService {

	private final TimeSlotClosureService closureService;
	private final TimeSlotClosureQueryService queryService;

	public TimeSlotClosureAdministrationService(
		TimeSlotClosureService closureService,
		TimeSlotClosureQueryService queryService
	) {
		this.closureService = closureService;
		this.queryService = queryService;
	}

	@Transactional(readOnly = true)
	public TimeSlotClosureView findLatest(long timeSlotId) {
		return queryService.findLatest(timeSlotId);
	}

	@Transactional
	public TimeSlotClosureView start(
		long timeSlotId,
		String actorSubject,
		String reason
	) {
		closureService.start(timeSlotId, actorSubject, reason);
		return queryService.findLatest(timeSlotId);
	}

	@Transactional
	public TimeSlotClosureView complete(
		long timeSlotId,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		closureService.complete(timeSlotId, actorSubject, reason, expectedVersion);
		return queryService.findLatest(timeSlotId);
	}

	@Transactional
	public TimeSlotClosureView withdraw(
		long timeSlotId,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		closureService.withdraw(timeSlotId, actorSubject, reason, expectedVersion);
		return queryService.findLatest(timeSlotId);
	}

	@Transactional
	public TimeSlotClosureView reopen(
		long timeSlotId,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		closureService.reopen(timeSlotId, actorSubject, reason, expectedVersion);
		return queryService.findLatest(timeSlotId);
	}
}
