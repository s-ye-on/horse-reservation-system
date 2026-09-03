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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	boolean existsByLessonDateAndStartTime(LocalDate lessonDate, LocalTime startTime);

	Optional<Reservation> findByIdAndMemberId(Long id, Long memberId);

	@Query(
		value = """
			select reservation
			from Reservation reservation
			where reservation.memberId = :memberId
			order by
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then 0 else 1 end,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.lessonDate end asc,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.startTime end asc,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.id end asc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.lessonDate end desc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.startTime end desc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.id end desc
			""",
		countQuery = """
			select count(reservation)
			from Reservation reservation
			where reservation.memberId = :memberId
			""")
	Page<Reservation> findMemberReservationsForDisplay(
		@Param("memberId") Long memberId,
		@Param("today") LocalDate today,
		@Param("currentTime") LocalTime currentTime,
		Pageable pageable
	);

	@Query(
		value = """
			select reservation
			from Reservation reservation
			where reservation.memberId = :memberId
			  and reservation.status = :status
			order by
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then 0 else 1 end,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.lessonDate end asc,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.startTime end asc,
				case when (
					reservation.lessonDate > :today
					or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
				) then reservation.id end asc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.lessonDate end desc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.startTime end desc,
				case when (
					reservation.lessonDate < :today
					or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
				) then reservation.id end desc
			""",
		countQuery = """
			select count(reservation)
			from Reservation reservation
			where reservation.memberId = :memberId
			  and reservation.status = :status
			""")
	Page<Reservation> findMemberReservationsByStatusForDisplay(
		@Param("memberId") Long memberId,
		@Param("status") ReservationStatus status,
		@Param("today") LocalDate today,
		@Param("currentTime") LocalTime currentTime,
		Pageable pageable
	);

	@Query(
		value = """
			select reservation
			from Reservation reservation
			where reservation.memberId = :memberId
			  and (:status is null or reservation.status = :status)
			  and (
				reservation.lessonDate > :today
				or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
			  )
			order by reservation.lessonDate asc, reservation.startTime asc, reservation.id asc
			""",
		countQuery = """
			select count(reservation)
			from Reservation reservation
			where reservation.memberId = :memberId
			  and (:status is null or reservation.status = :status)
			  and (
				reservation.lessonDate > :today
				or (reservation.lessonDate = :today and reservation.startTime > :currentTime)
			  )
			""")
	Page<Reservation> findUpcomingMemberReservations(
		@Param("memberId") Long memberId,
		@Param("status") ReservationStatus status,
		@Param("today") LocalDate today,
		@Param("currentTime") LocalTime currentTime,
		Pageable pageable
	);

	@Query(
		value = """
			select reservation
			from Reservation reservation
			where reservation.memberId = :memberId
			  and (:status is null or reservation.status = :status)
			  and (
				reservation.lessonDate < :today
				or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
			  )
			order by reservation.lessonDate desc, reservation.startTime desc, reservation.id desc
			""",
		countQuery = """
			select count(reservation)
			from Reservation reservation
			where reservation.memberId = :memberId
			  and (:status is null or reservation.status = :status)
			  and (
				reservation.lessonDate < :today
				or (reservation.lessonDate = :today and reservation.startTime <= :currentTime)
			  )
			""")
	Page<Reservation> findPastMemberReservations(
		@Param("memberId") Long memberId,
		@Param("status") ReservationStatus status,
		@Param("today") LocalDate today,
		@Param("currentTime") LocalTime currentTime,
		Pageable pageable
	);

	@Query("""
		select reservation.lessonDate as lessonDate,
			reservation.startTime as startTime,
			reservation.memberId as memberId,
			reservation.couponId as couponId
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

	@Query("""
		select reservation.lessonDate as lessonDate,
			reservation.status as status,
			count(reservation) as reservationCount
		from Reservation reservation
		where reservation.lessonDate between :lessonDateFrom and :lessonDateTo
		group by reservation.lessonDate, reservation.status
		order by reservation.lessonDate, reservation.status
		""")
	List<ReservationSummaryProjection> findReservationSummary(
		@Param("lessonDateFrom") LocalDate lessonDateFrom,
		@Param("lessonDateTo") LocalDate lessonDateTo
	);

	@Query("""
		select member.id as memberId,
			member.name as memberName,
			count(reservation.id) as completedRideCount
		from Reservation reservation, Member member
		where member.id = reservation.memberId
		  and reservation.status = :status
		  and reservation.lessonDate >= :lessonDateFrom
		  and reservation.lessonDate < :lessonDateToExclusive
		  and reservation.ridingClass in :ridingClasses
		group by member.id, member.name
		order by count(reservation.id) desc, member.id asc
		""")
	List<MonthlyRideMemberCountProjection> findMonthlyCompletedRideCounts(
		@Param("lessonDateFrom") LocalDate lessonDateFrom,
		@Param("lessonDateToExclusive") LocalDate lessonDateToExclusive,
		@Param("status") ReservationStatus status,
		@Param("ridingClasses") Collection<RidingClass> ridingClasses
	);

	@Query("""
		select reservation.id as reservationId,
			reservation.lessonDate as lessonDate,
			reservation.startTime as startTime,
			member.id as memberId,
			member.name as memberName,
			reservation.ridingClass as ridingClass,
			reservation.status as status
		from Reservation reservation, Member member
		where member.id = reservation.memberId
		  and reservation.lessonDate between :lessonDateFrom and :lessonDateTo
		  and reservation.status in :statuses
		order by reservation.lessonDate, reservation.startTime, reservation.id
		""")
	List<WeeklyOperationsReservationProjection> findWeeklyOperationsReservations(
		@Param("lessonDateFrom") LocalDate lessonDateFrom,
		@Param("lessonDateTo") LocalDate lessonDateTo,
		@Param("statuses") Collection<ReservationStatus> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT reservation FROM Reservation reservation WHERE reservation.id = :reservationId")
	Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

	@Query("""
		select reservation.id
		from Reservation reservation
		where reservation.status = :status
		  and (
			reservation.paymentDueAt <= :dueAt
			or reservation.lessonDate < :lessonDate
			or (reservation.lessonDate = :lessonDate and reservation.startTime <= :startTime)
		  )
		order by reservation.lessonDate, reservation.memberId, reservation.id
		""")
	List<Long> findPaymentExpiryCandidateIds(
		@Param("status") ReservationStatus status,
		@Param("dueAt") LocalDateTime dueAt,
		@Param("lessonDate") LocalDate lessonDate,
		@Param("startTime") LocalTime startTime
	);

	@Query("""
		select reservation.id
		from Reservation reservation
		where reservation.status = :status
		  and (
			reservation.lessonDate < :lessonDate
			or (reservation.lessonDate = :lessonDate and reservation.startTime <= :startTime)
		  )
		order by reservation.lessonDate, reservation.memberId, reservation.id
		""")
	List<Long> findApprovalExpiryCandidateIds(
		@Param("status") ReservationStatus status,
		@Param("lessonDate") LocalDate lessonDate,
		@Param("startTime") LocalTime startTime
	);

	@Query("""
		select reservation
		from Reservation reservation
		where reservation.status in :statuses
		order by reservation.paymentDueAt, reservation.id
		""")
	List<Reservation> findPendingPaymentOperations(
		@Param("statuses") Collection<ReservationStatus> statuses
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

	@Query(value = """
		SELECT reservation.id
		FROM reservations reservation
		FORCE INDEX (idx_reservations_occupancy)
		WHERE reservation.lesson_date = :lessonDate
		  AND reservation.start_time = :startTime
		ORDER BY reservation.id
		FOR UPDATE
		""", nativeQuery = true)
	@Transactional(propagation = Propagation.MANDATORY)
	List<Long> findHistoryIdsByLessonDateAndStartTimeForUpdate(
		@Param("lessonDate") LocalDate lessonDate,
		@Param("startTime") LocalTime startTime
	);

	@Query(value = """
		SELECT reservation.*
		FROM reservations reservation
		FORCE INDEX (idx_reservations_member_date_active_interval)
		WHERE reservation.member_id = :memberId
		  AND reservation.lesson_date = :lessonDate
		  AND reservation.active_slot_guard = 1
		  AND reservation.start_time < :candidateEndTime
		  AND reservation.end_time > :candidateStartTime
		ORDER BY reservation.start_time, reservation.id
		FOR UPDATE
		""", nativeQuery = true)
	@Transactional(propagation = Propagation.MANDATORY)
	List<Reservation> findActiveOverlapsForUpdate(
		@Param("memberId") Long memberId,
		@Param("lessonDate") LocalDate lessonDate,
		@Param("candidateStartTime") LocalTime candidateStartTime,
		@Param("candidateEndTime") LocalTime candidateEndTime
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

	@Query("""
		select reservation.id
		from Reservation reservation
		where reservation.lessonDate = :lessonDate
		  and reservation.status in :statuses
		order by reservation.id
		""")
	List<Long> findActiveIdsByLessonDateOrderById(
		@Param("lessonDate") LocalDate lessonDate,
		@Param("statuses") Collection<ReservationStatus> statuses
	);

	long countByLessonDateAndStatusIn(
		LocalDate lessonDate,
		Collection<ReservationStatus> statuses
	);
}
