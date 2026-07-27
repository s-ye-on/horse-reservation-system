package com.horse.schedules.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleDateStatus;

@Service
public class ScheduleDateAdministrationService {

	private final ScheduleDateClosureService closureService;
	private final ScheduleDateQueryService queryService;

	public ScheduleDateAdministrationService(
		ScheduleDateClosureService closureService,
		ScheduleDateQueryService queryService
	) {
		this.closureService = closureService;
		this.queryService = queryService;
	}

	@Transactional
	public ScheduleDateAdministrationResult startClosing(
		LocalDate scheduleDate,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		return result(
			closureService.start(scheduleDate, actorSubject, reason, expectedVersion));
	}

	@Transactional
	public ScheduleDateAdministrationResult close(
		LocalDate scheduleDate,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		return result(
			closureService.finalizeClosure(
				scheduleDate,
				actorSubject,
				reason,
				expectedVersion));
	}

	@Transactional
	public ScheduleDateAdministrationResult cancelClosing(
		LocalDate scheduleDate,
		String actorSubject,
		String reason,
		long expectedVersion
	) {
		return result(
			closureService.cancelClosing(
				scheduleDate,
				actorSubject,
				reason,
				expectedVersion));
	}

	@Transactional(readOnly = true)
	public ScheduleDateAdministrationResult getImpact(LocalDate scheduleDate) {
		final ScheduleDateView date = queryService.find(scheduleDate);
		final List<ScheduleImpactReservationView> reservations =
			queryService.findActiveReservations(scheduleDate);
		final int initialCount = initialCount(date, reservations.size());
		return new ScheduleDateAdministrationResult(
			new ScheduleDateClosureResult(
				scheduleDate,
				date.status(),
				date.resumeStatus(),
				reservations.size(),
				false),
			date,
			initialCount,
			reservations);
	}

	private ScheduleDateAdministrationResult result(ScheduleDateClosureResult closure) {
		final ScheduleDateView date = queryService.find(closure.scheduleDate());
		final List<ScheduleImpactReservationView> reservations =
			queryService.findActiveReservations(closure.scheduleDate());
		final int initialCount = initialCount(date, reservations.size());
		return new ScheduleDateAdministrationResult(
			closure,
			date,
			initialCount,
			reservations);
	}

	private int initialCount(ScheduleDateView date, int currentCount) {
		if (date.status() != ScheduleDateStatus.CLOSING
			&& date.status() != ScheduleDateStatus.CLOSED) {
			return currentCount;
		}
		return queryService.findInitialReservationCount(
			date.scheduleDate(),
			currentCount);
	}
}
