package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	@Query("""
		select reservation.lessonDate as lessonDate, reservation.startTime as startTime
		from Reservation reservation
		where reservation.id = :reservationId
		""")
	Optional<ReservationTimeSlotProjection> findTimeSlotById(@Param("reservationId") Long reservationId);

	@Query(
		value = """
			select reservation
			from Reservation reservation, Member member
			where member.id = reservation.memberId
			  and (:status is null or reservation.status = :status)
			  and (:lessonDateFrom is null or reservation.lessonDate >= :lessonDateFrom)
			  and (:lessonDateTo is null or reservation.lessonDate <= :lessonDateTo)
			  and (:ridingClass is null or reservation.ridingClass = :ridingClass)
			  and (
				:keyword is null
				or member.name like concat('%', :keyword, '%')
				or member.phone like concat('%', :keyword, '%')
			  )
			order by reservation.lessonDate, reservation.startTime, reservation.id
			""",
		countQuery = """
			select count(reservation)
			from Reservation reservation, Member member
			where member.id = reservation.memberId
			  and (:status is null or reservation.status = :status)
			  and (:lessonDateFrom is null or reservation.lessonDate >= :lessonDateFrom)
			  and (:lessonDateTo is null or reservation.lessonDate <= :lessonDateTo)
			  and (:ridingClass is null or reservation.ridingClass = :ridingClass)
			  and (
				:keyword is null
				or member.name like concat('%', :keyword, '%')
				or member.phone like concat('%', :keyword, '%')
			  )
			""")
	Page<Reservation> findAdminReservations(
		@Param("status") ReservationStatus status,
		@Param("lessonDateFrom") LocalDate lessonDateFrom,
		@Param("lessonDateTo") LocalDate lessonDateTo,
		@Param("ridingClass") RidingClass ridingClass,
		@Param("keyword") String keyword,
		Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT reservation FROM Reservation reservation WHERE reservation.id = :reservationId")
	Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select reservation
		from Reservation reservation
		where reservation.status = :status
		  and reservation.paymentDueAt <= :dueAt
		order by reservation.id
		""")
	List<Reservation> findPaymentDueReservationsForUpdate(
		@Param("status") ReservationStatus status,
		@Param("dueAt") LocalDateTime dueAt
	);

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
