package com.horse.timeslots.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotClosure;
import com.horse.timeslots.domain.TimeSlotClosureImpact;
import com.horse.timeslots.domain.TimeSlotClosureStatus;
import com.horse.timeslots.domain.TimeSlotOperationTimePolicy;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;
import com.horse.timeslots.infrastructure.TimeSlotClosureImpactRepository;
import com.horse.timeslots.infrastructure.TimeSlotClosureRepository;

@Service
public class TimeSlotClosureService {

	private static final String STARTED = "TIME_SLOT_CLOSURE_STARTED";
	private static final String COMPLETED = "TIME_SLOT_CLOSURE_COMPLETED";
	private static final String WITHDRAWN = "TIME_SLOT_CLOSURE_WITHDRAWN";
	private static final String REOPENED = "TIME_SLOT_CLOSURE_REOPENED";

	private final Clock clock;
	private final ScheduleDateRepository scheduleDateRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final TimeSlotClosureRepository closureRepository;
	private final TimeSlotClosureImpactRepository impactRepository;
	private final ReservationRepository reservationRepository;
	private final ScheduleAuditLogRepository auditLogRepository;

	public TimeSlotClosureService(
		Clock clock,
		ScheduleDateRepository scheduleDateRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		TimeSlotClosureRepository closureRepository,
		TimeSlotClosureImpactRepository impactRepository,
		ReservationRepository reservationRepository,
		ScheduleAuditLogRepository auditLogRepository
	) {
		this.clock = clock;
		this.scheduleDateRepository = scheduleDateRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.closureRepository = closureRepository;
		this.impactRepository = impactRepository;
		this.reservationRepository = reservationRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public TimeSlotCapacity start(Long timeSlotId, String actorSubject, String reason) {
		final ScheduleDate scheduleDate = lockScheduleDate(findLessonDate(timeSlotId));
		scheduleDate.ensureReservationInflowAllowed();
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		ensureNotStarted(timeSlot);

		final List<TimeSlotClosure> closures = closureRepository.findAllByTimeSlotIdForUpdate(timeSlotId);
		if (closures.stream().anyMatch(closure -> closure.getStatus() == TimeSlotClosureStatus.IN_PROGRESS)
			|| timeSlot.isAdminClosed()) {
			return timeSlot;
		}
		timeSlot.changeAdminClosed(true);
		final List<Reservation> activeReservations = reservationRepository
			.findOccupyingByLessonDateAndStartTimeForUpdate(
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				ReservationStatus.occupyingStatuses());
		final LocalDateTime occurredAt = LocalDateTime.now(clock);
		final TimeSlotClosure closure = closureRepository.saveAndFlush(TimeSlotClosure.start(
			timeSlot.getId(),
			reason,
			actorSubject,
			occurredAt));
		impactRepository.saveAll(activeReservations.stream()
			.map(reservation -> TimeSlotClosureImpact.create(
				closure.getId(),
				reservation.getId(),
				reservation.getStatus()))
			.toList());
		appendAudit(timeSlot, closure, STARTED, actorSubject, reason, activeReservations.size());
		if (activeReservations.isEmpty()) {
			closure.complete(actorSubject, occurredAt);
			appendAudit(timeSlot, closure, COMPLETED, actorSubject, reason, 0);
		}
		return timeSlot;
	}

	@Transactional
	public TimeSlotCapacity complete(Long timeSlotId, String actorSubject, String reason) {
		return complete(timeSlotId, actorSubject, reason, null);
	}

	@Transactional
	public TimeSlotCapacity complete(
		Long timeSlotId,
		String actorSubject,
		String reason,
		Long expectedVersion
	) {
		lockScheduleDate(findLessonDate(timeSlotId));
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		final TimeSlotClosure closure = findInProgress(timeSlotId);
		ensureVersion(closure, expectedVersion);
		final long unresolved = countUnresolved(closure, timeSlot);
		final int activeCount = reservationRepository.findOccupyingByLessonDateAndStartTimeForUpdate(
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			ReservationStatus.occupyingStatuses()).size();
		if (unresolved > 0 || activeCount > 0) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_IMPACTS_UNRESOLVED);
		}
		closure.complete(actorSubject, LocalDateTime.now(clock));
		appendAudit(timeSlot, closure, COMPLETED, actorSubject, reason, 0);
		return timeSlot;
	}

	@Transactional
	public TimeSlotCapacity withdraw(Long timeSlotId, String actorSubject, String reason) {
		return withdraw(timeSlotId, actorSubject, reason, null);
	}

	@Transactional
	public TimeSlotCapacity withdraw(
		Long timeSlotId,
		String actorSubject,
		String reason,
		Long expectedVersion
	) {
		final ScheduleDate scheduleDate = lockScheduleDate(findLessonDate(timeSlotId));
		scheduleDate.ensureReservationInflowAllowed();
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		ensureNotStarted(timeSlot);
		if (!timeSlot.isAdminClosed()) {
			return timeSlot;
		}
		final TimeSlotClosure closure = findInProgress(timeSlotId);
		ensureVersion(closure, expectedVersion);
		final long total = impactRepository.countByClosureId(closure.getId());
		if (countUnresolved(closure, timeSlot) != total) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED);
		}
		closure.withdraw(actorSubject, LocalDateTime.now(clock));
		timeSlot.changeAdminClosed(false);
		appendAudit(timeSlot, closure, WITHDRAWN, actorSubject, reason, Math.toIntExact(total));
		return timeSlot;
	}

	@Transactional
	public TimeSlotCapacity reopen(Long timeSlotId, String actorSubject, String reason) {
		return reopen(timeSlotId, actorSubject, reason, null);
	}

	@Transactional
	public TimeSlotCapacity reopen(
		Long timeSlotId,
		String actorSubject,
		String reason,
		Long expectedVersion
	) {
		final ScheduleDate scheduleDate = lockScheduleDate(findLessonDate(timeSlotId));
		scheduleDate.ensureReservationInflowAllowed();
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		ensureNotStarted(timeSlot);
		if (!timeSlot.isAdminClosed()) {
			return timeSlot;
		}
		final List<TimeSlotClosure> closures = closureRepository.findAllByTimeSlotIdForUpdate(timeSlotId);
		final TimeSlotClosure latest = closures.isEmpty() ? null : closures.get(closures.size() - 1);
		if (latest == null || latest.getStatus() != TimeSlotClosureStatus.COMPLETED) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS);
		}
		ensureVersion(latest, expectedVersion);
		timeSlot.changeAdminClosed(false);
		appendAudit(timeSlot, latest, REOPENED, actorSubject, reason, 0);
		return timeSlot;
	}

	@Transactional
	public TimeSlotCapacity withdrawOrReopen(
		Long timeSlotId,
		String actorSubject,
		String reason
	) {
		final ScheduleDate scheduleDate = lockScheduleDate(findLessonDate(timeSlotId));
		scheduleDate.ensureReservationInflowAllowed();
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		ensureNotStarted(timeSlot);
		if (!timeSlot.isAdminClosed()) {
			return timeSlot;
		}
		final List<TimeSlotClosure> closures = closureRepository.findAllByTimeSlotIdForUpdate(timeSlotId);
		final TimeSlotClosure latest = closures.isEmpty() ? null : closures.get(closures.size() - 1);
		if (latest == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS);
		}
		if (latest.getStatus() == TimeSlotClosureStatus.IN_PROGRESS) {
			final long total = impactRepository.countByClosureId(latest.getId());
			if (countUnresolved(latest, timeSlot) != total) {
				throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED);
			}
			latest.withdraw(actorSubject, LocalDateTime.now(clock));
			timeSlot.changeAdminClosed(false);
			appendAudit(
				timeSlot,
				latest,
				WITHDRAWN,
				actorSubject,
				reason,
				Math.toIntExact(total));
			return timeSlot;
		}
		if (latest.getStatus() != TimeSlotClosureStatus.COMPLETED) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS);
		}
		timeSlot.changeAdminClosed(false);
		appendAudit(timeSlot, latest, REOPENED, actorSubject, reason, 0);
		return timeSlot;
	}

	private long countUnresolved(TimeSlotClosure closure, TimeSlotCapacity timeSlot) {
		return impactRepository.countUnresolved(
			closure.getId(),
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			ReservationStatus.occupyingStatuses());
	}

	private void ensureVersion(TimeSlotClosure closure, Long expectedVersion) {
		if (expectedVersion != null && closure.getVersion() != expectedVersion) {
			throw new TimeSlotException(
				ExceptionCode.TIMESLOT_CLOSURE_VERSION_CONFLICT,
				Map.of("currentVersion", closure.getVersion()));
		}
	}

	private TimeSlotClosure findInProgress(Long timeSlotId) {
		return closureRepository.findInProgressByTimeSlotIdForUpdate(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS));
	}

	private ScheduleDate lockScheduleDate(java.time.LocalDate lessonDate) {
		return scheduleDateRepository.findByScheduleDateForUpdate(lessonDate)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_LESSON_DATE));
	}

	private java.time.LocalDate findLessonDate(Long timeSlotId) {
		return timeSlotRepository.findLessonDateById(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

	private TimeSlotCapacity lockTimeSlot(Long timeSlotId) {
		return timeSlotRepository.findByIdForUpdate(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

	private void ensureNotStarted(TimeSlotCapacity timeSlot) {
		TimeSlotOperationTimePolicy.ensureNotStarted(
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			LocalDateTime.now(clock));
	}

	private void appendAudit(
		TimeSlotCapacity timeSlot,
		TimeSlotClosure closure,
		String action,
		String actorSubject,
		String reason,
		int impactCount
	) {
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.TIME_SLOT,
			timeSlot.getId().toString(),
			action,
			null,
			Map.of(
				"closureId", closure.getId(),
				"status", closure.getStatus().name(),
				"adminClosed", timeSlot.isAdminClosed()),
			actorSubject,
			reason,
			Map.of("impactCount", impactCount)));
	}
}
