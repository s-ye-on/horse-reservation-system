package com.horse.timeslots.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.horse.members.domain.RidingClass;

public interface TimeSlotReservationHistoryQuery {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	List<RidingClass> findActiveRidingClassesForUpdate(LocalDate lessonDate, LocalTime startTime);

}
