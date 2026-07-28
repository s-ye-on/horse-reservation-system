package com.horse.reservations.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.domain.ReservationApplicationIdempotency;

public interface ReservationApplicationIdempotencyRepository
	extends JpaRepository<ReservationApplicationIdempotency, Long> {

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM reservation_application_idempotencies
		FORCE INDEX (uk_reservation_application_idempotency_scope)
		WHERE auth_subject = :authSubject
		  AND operation = :operation
		  AND idempotency_key = :idempotencyKey
		FOR UPDATE
		""", nativeQuery = true)
	Optional<ReservationApplicationIdempotency> findForUpdate(
		@Param("authSubject") String authSubject,
		@Param("operation") String operation,
		@Param("idempotencyKey") String idempotencyKey
	);
}
