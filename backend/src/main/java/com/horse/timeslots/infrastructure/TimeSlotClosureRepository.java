package com.horse.timeslots.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.timeslots.domain.TimeSlotClosure;

public interface TimeSlotClosureRepository extends JpaRepository<TimeSlotClosure, Long> {

	Optional<TimeSlotClosure> findFirstByTimeSlotIdOrderByIdDesc(Long timeSlotId);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM time_slot_closures
		WHERE time_slot_id = :timeSlotId
		  AND status = 'IN_PROGRESS'
		ORDER BY id
		FOR UPDATE
		""", nativeQuery = true)
	Optional<TimeSlotClosure> findInProgressByTimeSlotIdForUpdate(
		@Param("timeSlotId") Long timeSlotId
	);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM time_slot_closures
		WHERE time_slot_id = :timeSlotId
		ORDER BY id
		FOR UPDATE
		""", nativeQuery = true)
	List<TimeSlotClosure> findAllByTimeSlotIdForUpdate(@Param("timeSlotId") Long timeSlotId);
}
