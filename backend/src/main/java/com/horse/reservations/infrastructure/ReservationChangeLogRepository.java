package com.horse.reservations.infrastructure;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.horse.reservations.domain.ReservationChangeLog;

public interface ReservationChangeLogRepository extends Repository<ReservationChangeLog, Long> {

	ReservationChangeLog save(ReservationChangeLog changeLog);

	List<ReservationChangeLog> findAllByReservationIdOrderByCreatedAtAscIdAsc(Long reservationId);
}
