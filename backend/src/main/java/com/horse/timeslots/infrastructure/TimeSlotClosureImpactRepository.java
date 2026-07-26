package com.horse.timeslots.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.timeslots.domain.TimeSlotClosureImpact;

public interface TimeSlotClosureImpactRepository
	extends JpaRepository<TimeSlotClosureImpact, Long> {

	List<TimeSlotClosureImpact> findAllByClosureIdOrderByReservationId(Long closureId);

	long countByClosureId(Long closureId);

	boolean existsByClosureIdAndReservationId(Long closureId, Long reservationId);

	@Query("""
		select count(impact)
		from TimeSlotClosureImpact impact, Reservation reservation
		where impact.closureId = :closureId
		  and reservation.id = impact.reservationId
		  and reservation.lessonDate = :lessonDate
		  and reservation.startTime = :startTime
		  and reservation.status in :activeStatuses
		""")
	long countUnresolved(
		@Param("closureId") Long closureId,
		@Param("lessonDate") java.time.LocalDate lessonDate,
		@Param("startTime") java.time.LocalTime startTime,
		@Param("activeStatuses") java.util.Collection<com.horse.reservations.domain.ReservationStatus> activeStatuses
	);
}
