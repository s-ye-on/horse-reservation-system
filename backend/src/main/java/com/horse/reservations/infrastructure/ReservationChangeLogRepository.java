package com.horse.reservations.infrastructure;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.ReservationActorType;

import jakarta.persistence.LockModeType;

public interface ReservationChangeLogRepository extends Repository<ReservationChangeLog, Long> {

	ReservationChangeLog save(ReservationChangeLog changeLog);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("""
		select changeLog
		from ReservationChangeLog changeLog
		where changeLog.reservationId = :reservationId
		  and changeLog.changeType = :changeType
		order by changeLog.id
		""")
	List<ReservationChangeLog> findByReservationIdAndChangeTypeForShare(
		@Param("reservationId") Long reservationId,
		@Param("changeType") ReservationChangeType changeType
	);

	List<ReservationChangeLog> findAllByReservationIdOrderByCreatedAtAscIdAsc(Long reservationId);

	@Query(
		value = """
			select changeLog.id as auditLogId,
				changeLog.reservationId as reservationId,
				member.id as memberId,
				member.name as memberName,
				changeLog.actorAuthSubject as actorAuthSubject,
				changeLog.actorType as actorType,
				changeLog.changeType as changeType,
				changeLog.fromStatus as fromStatus,
				changeLog.toStatus as toStatus,
				changeLog.fromLessonDate as fromLessonDate,
				changeLog.fromStartTime as fromStartTime,
				changeLog.toLessonDate as toLessonDate,
				changeLog.toStartTime as toStartTime,
				changeLog.couponAction as couponAction,
				changeLog.memo as memo,
				changeLog.createdAt as occurredAt
			from ReservationChangeLog changeLog, Reservation reservation, Member member
			where reservation.id = changeLog.reservationId
			  and member.id = reservation.memberId
			  and (:reservationId is null or changeLog.reservationId = :reservationId)
			  and (
				:keyword is null
				or member.name like concat('%', :keyword, '%')
				or member.phone like concat('%', :keyword, '%')
			  )
			  and (:occurredAtFrom is null or changeLog.createdAt >= :occurredAtFrom)
			  and (:occurredAtTo is null or changeLog.createdAt <= :occurredAtTo)
			  and (:actorType is null or changeLog.actorType = :actorType)
			  and (:changeType is null or changeLog.changeType = :changeType)
			order by changeLog.createdAt desc, changeLog.id desc
			""",
		countQuery = """
			select count(changeLog)
			from ReservationChangeLog changeLog, Reservation reservation, Member member
			where reservation.id = changeLog.reservationId
			  and member.id = reservation.memberId
			  and (:reservationId is null or changeLog.reservationId = :reservationId)
			  and (
				:keyword is null
				or member.name like concat('%', :keyword, '%')
				or member.phone like concat('%', :keyword, '%')
			  )
			  and (:occurredAtFrom is null or changeLog.createdAt >= :occurredAtFrom)
			  and (:occurredAtTo is null or changeLog.createdAt <= :occurredAtTo)
			  and (:actorType is null or changeLog.actorType = :actorType)
			  and (:changeType is null or changeLog.changeType = :changeType)
			""")
	Page<AdminReservationAuditProjection> findAdminAuditLogs(
		@Param("keyword") String keyword,
		@Param("reservationId") Long reservationId,
		@Param("occurredAtFrom") LocalDateTime occurredAtFrom,
		@Param("occurredAtTo") LocalDateTime occurredAtTo,
		@Param("actorType") ReservationActorType actorType,
		@Param("changeType") ReservationChangeType changeType,
		Pageable pageable
	);
}
