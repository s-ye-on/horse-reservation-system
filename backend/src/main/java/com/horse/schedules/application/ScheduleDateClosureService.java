package com.horse.schedules.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.ScheduleDateStatus;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ScheduleDateClosureService {

	private static final String CLOSING_STARTED = "CLOSING_STARTED";
	private static final String CLOSED = "CLOSED";
	private static final String CLOSING_CANCELLED = "CLOSING_CANCELLED";

	private final Clock clock;
	private final ScheduleDateRepository scheduleDateRepository;
	private final ReservationRepository reservationRepository;
	private final ScheduleAuditLogRepository auditLogRepository;

	public ScheduleDateClosureService(
		Clock clock,
		ScheduleDateRepository scheduleDateRepository,
		ReservationRepository reservationRepository,
		ScheduleAuditLogRepository auditLogRepository
	) {
		this.clock = clock;
		this.scheduleDateRepository = scheduleDateRepository;
		this.reservationRepository = reservationRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public ScheduleDateClosureResult start(
		LocalDate scheduleDateValue,
		String actorAuthSubject,
		String reason
	) {
		final ScheduleDate scheduleDate = findForUpdate(scheduleDateValue);
		final Map<String, Object> fromState = stateOf(scheduleDate);
		final boolean closingStarted = scheduleDate.startClosing(
			LocalDate.now(clock),
			actorAuthSubject,
			reason);
		if (closingStarted) {
			appendAudit(
				scheduleDate,
				CLOSING_STARTED,
				fromState,
				stateOf(scheduleDate),
				actorAuthSubject,
				reason,
				Map.of());
		}

		final List<Long> activeIds = activeReservationIds(scheduleDateValue);
		if (activeIds.isEmpty() && scheduleDate.getStatus() == ScheduleDateStatus.CLOSING) {
			final Map<String, Object> closingState = stateOf(scheduleDate);
			scheduleDate.finalizeClosed(actorAuthSubject);
			appendAudit(
				scheduleDate,
				CLOSED,
				closingState,
				stateOf(scheduleDate),
				actorAuthSubject,
				reason,
				Map.of("activeReservationCount", 0));
		}
		return result(scheduleDate, activeIds.size(), closingStarted);
	}

	@Transactional(readOnly = true)
	public ScheduleDateClosureImpact impact(LocalDate scheduleDateValue) {
		find(scheduleDateValue);
		final List<Long> activeIds = activeReservationIds(scheduleDateValue);
		return new ScheduleDateClosureImpact(
			scheduleDateValue,
			activeIds,
			activeIds.size());
	}

	@Transactional
	public ScheduleDateClosureResult finalizeClosure(
		LocalDate scheduleDateValue,
		String actorAuthSubject,
		String reason
	) {
		final ScheduleDate scheduleDate = findForUpdate(scheduleDateValue);
		scheduleDate.ensureClosureCleanupAllowed();
		final long activeCount = reservationRepository.countByLessonDateAndStatusIn(
			scheduleDateValue,
			ReservationStatus.occupyingStatuses());
		if (activeCount > 0) {
			throw new ScheduleException(
				ExceptionCode.TIMESLOT_ACTIVE_RESERVATIONS_EXIST_ON_CLOSURE_DATE);
		}
		final Map<String, Object> fromState = stateOf(scheduleDate);
		final boolean changed = scheduleDate.finalizeClosed(actorAuthSubject);
		if (changed) {
			appendAudit(
				scheduleDate,
				CLOSED,
				fromState,
				stateOf(scheduleDate),
				actorAuthSubject,
				reason,
				Map.of("activeReservationCount", 0));
		}
		return result(scheduleDate, 0, changed);
	}

	@Transactional
	public ScheduleDateClosureResult cancelClosing(
		LocalDate scheduleDateValue,
		String actorAuthSubject,
		String reason
	) {
		final ScheduleDate scheduleDate = findForUpdate(scheduleDateValue);
		final Map<String, Object> fromState = stateOf(scheduleDate);
		final boolean changed = scheduleDate.cancelClosing(actorAuthSubject, reason);
		if (changed) {
			appendAudit(
				scheduleDate,
				CLOSING_CANCELLED,
				fromState,
				stateOf(scheduleDate),
				actorAuthSubject,
				reason,
				Map.of(
					"activeReservationCount",
					reservationRepository.countByLessonDateAndStatusIn(
						scheduleDateValue,
						ReservationStatus.occupyingStatuses())));
		}
		final int activeCount = Math.toIntExact(
			reservationRepository.countByLessonDateAndStatusIn(
				scheduleDateValue,
				ReservationStatus.occupyingStatuses()));
		return result(scheduleDate, activeCount, changed);
	}

	private ScheduleDate findForUpdate(LocalDate scheduleDate) {
		return scheduleDateRepository.findByScheduleDateForUpdate(scheduleDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
	}

	private ScheduleDate find(LocalDate scheduleDate) {
		return scheduleDateRepository.findByScheduleDate(scheduleDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
	}

	private List<Long> activeReservationIds(LocalDate scheduleDate) {
		return reservationRepository.findActiveIdsByLessonDateOrderById(
			scheduleDate,
			ReservationStatus.occupyingStatuses());
	}

	private ScheduleDateClosureResult result(
		ScheduleDate scheduleDate,
		int activeReservationCount,
		boolean changed
	) {
		return new ScheduleDateClosureResult(
			scheduleDate.getScheduleDate(),
			scheduleDate.getStatus(),
			scheduleDate.getResumeStatus(),
			activeReservationCount,
			changed);
	}

	private void appendAudit(
		ScheduleDate scheduleDate,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason,
		Map<String, Object> metadata
	) {
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.SCHEDULE_DATE,
			scheduleDate.getScheduleDate().toString(),
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason,
			metadata));
	}

	private Map<String, Object> stateOf(ScheduleDate scheduleDate) {
		return Map.of(
			"status", scheduleDate.getStatus().name(),
			"resumeStatus", nullableStatus(scheduleDate.getResumeStatus()));
	}

	private String nullableStatus(ScheduleDateStatus status) {
		return status == null ? "NONE" : status.name();
	}
}
