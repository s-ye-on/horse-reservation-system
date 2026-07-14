package com.horse.reservations.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationChangeType;

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
}
