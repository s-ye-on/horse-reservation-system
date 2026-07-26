package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotClosure;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;
import com.horse.timeslots.infrastructure.TimeSlotClosureRepository;

@Service
public class TimeSlotClosureCommandLockService {

	private final TimeSlotCapacityRepository timeSlotRepository;
	private final TimeSlotClosureRepository closureRepository;

	public TimeSlotClosureCommandLockService(
		TimeSlotCapacityRepository timeSlotRepository,
		TimeSlotClosureRepository closureRepository
	) {
		this.timeSlotRepository = timeSlotRepository;
		this.closureRepository = closureRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void ensureCommandAllowed(LocalDate lessonDate, LocalTime startTime) {
		final TimeSlotCapacity timeSlot = lockTimeSlot(lessonDate, startTime).orElse(null);
		if (timeSlot == null) {
			return;
		}
		if (timeSlot.isAdminClosed()) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED);
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<TimeSlotClosure> findInProgress(LocalDate lessonDate, LocalTime startTime) {
		final Optional<TimeSlotCapacity> timeSlot = lockTimeSlot(lessonDate, startTime);
		if (timeSlot.isEmpty()) {
			return Optional.empty();
		}
		return closureRepository.findInProgressByTimeSlotIdForUpdate(timeSlot.get().getId());
	}

	private Optional<TimeSlotCapacity> lockTimeSlot(LocalDate lessonDate, LocalTime startTime) {
		return timeSlotRepository.findByLessonDateAndStartTimeForUpdate(lessonDate, startTime);
	}
}
