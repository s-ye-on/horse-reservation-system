package com.horse.timeslots.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.timeslots.domain.TimeSlotCapacity;

import jakarta.persistence.LockModeType;

public interface TimeSlotCapacityRepository extends JpaRepository<TimeSlotCapacity, Long> {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	List<TimeSlotCapacity> findAllByOrderByLessonDateAscStartTimeAsc();

	List<TimeSlotCapacity> findAllByLessonDateOrderByStartTimeAsc(LocalDate lessonDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select timeSlot from TimeSlotCapacity timeSlot where timeSlot.id = :timeSlotId")
	Optional<TimeSlotCapacity> findByIdForUpdate(@Param("timeSlotId") Long timeSlotId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select timeSlot
		from TimeSlotCapacity timeSlot
		where timeSlot.lessonDate = :lessonDate
		  and timeSlot.startTime = :startTime
		""")
	Optional<TimeSlotCapacity> findByLessonDateAndStartTimeForUpdate(
		@Param("lessonDate") LocalDate lessonDate,
		@Param("startTime") LocalTime startTime
	);

}
