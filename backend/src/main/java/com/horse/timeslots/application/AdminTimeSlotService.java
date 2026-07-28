package com.horse.timeslots.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.schedules.application.ScheduleDateInflowLockService;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotOperationTimePolicy;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class AdminTimeSlotService {

	private final Clock clock;
	private final ScheduleDateInflowLockService scheduleDateInflowLockService;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final TimeSlotReservationHistoryQuery reservationHistoryQuery;
	private final TimeSlotClosureService closureService;

	public AdminTimeSlotService(
		Clock clock,
		ScheduleDateInflowLockService scheduleDateInflowLockService,
		TimeSlotCapacityRepository timeSlotRepository,
		TimeSlotReservationHistoryQuery reservationHistoryQuery,
		TimeSlotClosureService closureService
	) {
		this.clock = clock;
		this.scheduleDateInflowLockService = scheduleDateInflowLockService;
		this.timeSlotRepository = timeSlotRepository;
		this.reservationHistoryQuery = reservationHistoryQuery;
		this.closureService = closureService;
	}

	@Transactional(readOnly = true)
	public List<TimeSlotResult> getTimeSlots() {
		final LocalDateTime requestedAt = LocalDateTime.now(clock);
		return timeSlotRepository.findUpcoming(requestedAt.toLocalDate(), requestedAt.toLocalTime()).stream()
			.map(TimeSlotResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TimeSlotResult> getTimeSlotHistory() {
		final LocalDateTime requestedAt = LocalDateTime.now(clock);
		return timeSlotRepository.findHistory(requestedAt.toLocalDate(), requestedAt.toLocalTime()).stream()
			.map(TimeSlotResult::from)
			.toList();
	}

	@Transactional
	public TimeSlotResult createTimeSlot(
		LocalDate lessonDate,
		LocalTime startTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		ensureNotStartedIfPresent(lessonDate, startTime);
		final TimeSlotCapacity timeSlot = TimeSlotCapacity.create(
			lessonDate,
			startTime,
			totalCapacity,
			roundArenaCapacity,
			classCapacities);
		scheduleDateInflowLockService.lock(timeSlot.getLessonDate());
		if (lessonDate != null
			&& startTime != null
			&& timeSlotRepository.existsByLessonDateAndStartTime(lessonDate, startTime)) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_ALREADY_EXISTS);
		}
		try {
			return TimeSlotResult.from(timeSlotRepository.saveAndFlush(timeSlot));
		}
		catch (DataIntegrityViolationException exception) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_ALREADY_EXISTS);
		}
	}

	@Transactional
	public TimeSlotResult changeClosedStatus(
		Long timeSlotId,
		Boolean closed,
		String actorSubject
	) {
		if (closed == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSED_STATUS);
		}
		final String reason = "기존 시간대 마감 API 요청";
		if (closed) {
			return TimeSlotResult.from(closureService.start(timeSlotId, actorSubject, reason));
		}
		return TimeSlotResult.from(closureService.withdrawOrReopen(
			timeSlotId,
			actorSubject,
			reason));
	}

	@Transactional
	public TimeSlotResult changeCapacity(
		Long timeSlotId,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		final TimeSlotCapacity timeSlot = lockTimeSlotAfterScheduleDate(timeSlotId);
		ensureNotStarted(timeSlot);
		final List<RidingClass> activeRidingClasses = reservationHistoryQuery.findActiveRidingClassesForUpdate(
			timeSlot.getLessonDate(),
			timeSlot.getStartTime());
		final int roundArenaOccupied = (int)activeRidingClasses.stream()
			.filter(TimeSlotCapacity::usesRoundArena)
			.count();
		final Map<RidingClass, Integer> classOccupied = activeRidingClasses.stream()
			.collect(Collectors.toMap(Function.identity(), ignored -> 1, Integer::sum));
		timeSlot.changeCapacity(
			totalCapacity,
			roundArenaCapacity,
			classCapacities,
			activeRidingClasses.size(),
			roundArenaOccupied,
			classOccupied);
		return TimeSlotResult.from(timeSlot);
	}

	@Transactional
	public void deleteTimeSlot(Long timeSlotId) {
		final TimeSlotCapacity timeSlot = lockTimeSlotAfterScheduleDate(timeSlotId);
		ensureNotStarted(timeSlot);
		if (reservationHistoryQuery.existsByLessonDateAndStartTime(
			timeSlot.getLessonDate(), timeSlot.getStartTime())) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_RESERVATION_HISTORY_EXISTS);
		}
		timeSlotRepository.delete(timeSlot);
		timeSlotRepository.flush();
	}

	private void ensureNotStartedIfPresent(LocalDate lessonDate, LocalTime startTime) {
		if (lessonDate != null && startTime != null) {
			TimeSlotOperationTimePolicy.ensureNotStarted(lessonDate, startTime, LocalDateTime.now(clock));
		}
	}

	private void ensureNotStarted(TimeSlotCapacity timeSlot) {
		TimeSlotOperationTimePolicy.ensureNotStarted(
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			LocalDateTime.now(clock));
	}

	private TimeSlotCapacity lockTimeSlotAfterScheduleDate(Long timeSlotId) {
		final LocalDate lessonDate = timeSlotRepository.findLessonDateById(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
		scheduleDateInflowLockService.lock(lessonDate);
		return getTimeSlotForUpdate(timeSlotId);
	}

	private TimeSlotCapacity getTimeSlotForUpdate(Long timeSlotId) {
		return timeSlotRepository.findByIdForUpdate(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

}
