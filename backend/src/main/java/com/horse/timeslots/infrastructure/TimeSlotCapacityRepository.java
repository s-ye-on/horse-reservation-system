package com.horse.timeslots.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
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

	Optional<TimeSlotCapacity> findByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	List<TimeSlotCapacity> findAllByOrderByLessonDateAscStartTimeAsc();

	@Query("""
		select timeSlot
		from TimeSlotCapacity timeSlot
		where timeSlot.lessonDate > :date
		   or (timeSlot.lessonDate = :date and timeSlot.startTime > :time)
		order by timeSlot.lessonDate, timeSlot.startTime, timeSlot.id
		""")
	List<TimeSlotCapacity> findUpcoming(
		@Param("date") LocalDate date,
		@Param("time") LocalTime time
	);

	@Query("""
		select timeSlot
		from TimeSlotCapacity timeSlot
		where timeSlot.lessonDate < :date
		   or (timeSlot.lessonDate = :date and timeSlot.startTime <= :time)
		order by timeSlot.lessonDate desc, timeSlot.startTime desc, timeSlot.id desc
		""")
	List<TimeSlotCapacity> findHistory(
		@Param("date") LocalDate date,
		@Param("time") LocalTime time
	);

	List<TimeSlotCapacity> findAllByLessonDateOrderByStartTimeAsc(LocalDate lessonDate);

	@Query(value = """
		SELECT *
		FROM time_slot_capacities
		FORCE INDEX (idx_time_slot_capacities_lesson_date_id)
		WHERE lesson_date = :lessonDate
		ORDER BY id
		FOR UPDATE
		""", nativeQuery = true)
	List<TimeSlotCapacity> findAllByLessonDateForUpdateOrdered(
		@Param("lessonDate") LocalDate lessonDate
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select timeSlot
		from TimeSlotCapacity timeSlot
		where timeSlot.id in :timeSlotIds
		order by timeSlot.id
		""")
	List<TimeSlotCapacity> findAllByIdForUpdateOrdered(@Param("timeSlotIds") Collection<Long> timeSlotIds);

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
