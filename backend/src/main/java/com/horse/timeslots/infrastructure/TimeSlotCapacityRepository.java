package com.horse.timeslots.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.timeslots.domain.TimeSlotCapacity;

public interface TimeSlotCapacityRepository extends JpaRepository<TimeSlotCapacity, Long> {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	List<TimeSlotCapacity> findAllByOrderByLessonDateAscStartTimeAsc();

}
