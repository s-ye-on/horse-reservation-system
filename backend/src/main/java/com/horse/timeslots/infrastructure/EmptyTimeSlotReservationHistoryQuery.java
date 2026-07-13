package com.horse.timeslots.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.horse.timeslots.application.TimeSlotReservationHistoryQuery;

@Component
public class EmptyTimeSlotReservationHistoryQuery implements TimeSlotReservationHistoryQuery {

	@Override
	public boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime) {
		return false;
	}

}
