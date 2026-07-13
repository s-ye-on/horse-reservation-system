package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalTime;

public interface TimeSlotReservationHistoryQuery {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

}
