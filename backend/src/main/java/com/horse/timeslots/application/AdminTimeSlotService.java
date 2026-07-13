package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class AdminTimeSlotService {

	private final TimeSlotCapacityRepository timeSlotRepository;
	private final TimeSlotReservationHistoryQuery reservationHistoryQuery;

	public AdminTimeSlotService(
		TimeSlotCapacityRepository timeSlotRepository,
		TimeSlotReservationHistoryQuery reservationHistoryQuery
	) {
		this.timeSlotRepository = timeSlotRepository;
		this.reservationHistoryQuery = reservationHistoryQuery;
	}

	@Transactional(readOnly = true)
	public List<TimeSlotResult> getTimeSlots() {
		return timeSlotRepository.findAllByOrderByLessonDateAscStartTimeAsc().stream()
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
		if (lessonDate != null
			&& startTime != null
			&& timeSlotRepository.existsByLessonDateAndStartTime(lessonDate, startTime)) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_ALREADY_EXISTS);
		}
		final TimeSlotCapacity timeSlot = TimeSlotCapacity.create(
			lessonDate,
			startTime,
			totalCapacity,
			roundArenaCapacity,
			classCapacities);
		try {
			return TimeSlotResult.from(timeSlotRepository.saveAndFlush(timeSlot));
		}
		catch (DataIntegrityViolationException exception) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_ALREADY_EXISTS);
		}
	}

	@Transactional
	public TimeSlotResult changeClosedStatus(Long timeSlotId, Boolean closed) {
		final TimeSlotCapacity timeSlot = getTimeSlot(timeSlotId);
		timeSlot.changeClosedStatus(closed);
		return TimeSlotResult.from(timeSlot);
	}

	@Transactional
	public TimeSlotResult changeCapacity(
		Long timeSlotId,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		final TimeSlotCapacity timeSlot = getTimeSlot(timeSlotId);
		timeSlot.changeCapacity(totalCapacity, roundArenaCapacity, classCapacities);
		return TimeSlotResult.from(timeSlot);
	}

	@Transactional
	public void deleteTimeSlot(Long timeSlotId) {
		final TimeSlotCapacity timeSlot = getTimeSlot(timeSlotId);
		if (reservationHistoryQuery.existsByLessonDateAndStartTime(
			timeSlot.getLessonDate(), timeSlot.getStartTime())) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_RESERVATION_HISTORY_EXISTS);
		}
		timeSlotRepository.delete(timeSlot);
		timeSlotRepository.flush();
	}

	private TimeSlotCapacity getTimeSlot(Long timeSlotId) {
		return timeSlotRepository.findById(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

}
