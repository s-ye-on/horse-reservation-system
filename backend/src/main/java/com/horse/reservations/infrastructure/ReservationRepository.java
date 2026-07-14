package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select reservation
		from Reservation reservation
		where reservation.lessonDate = :lessonDate
		  and reservation.startTime = :startTime
		  and reservation.status in :statuses
		order by reservation.id
		""")
	List<Reservation> findOccupyingByLessonDateAndStartTimeForUpdate(
		@Param("lessonDate") LocalDate lessonDate,
		@Param("startTime") LocalTime startTime,
		@Param("statuses") Collection<ReservationStatus> statuses
	);

	@Query("""
		select reservation
		from Reservation reservation
		where reservation.lessonDate = :lessonDate
		  and reservation.status in :statuses
		order by reservation.startTime, reservation.id
		""")
	List<Reservation> findOccupyingByLessonDate(
		@Param("lessonDate") LocalDate lessonDate,
		@Param("statuses") Collection<ReservationStatus> statuses
	);
}
