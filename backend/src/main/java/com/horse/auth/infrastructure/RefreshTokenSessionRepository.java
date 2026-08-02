package com.horse.auth.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.auth.domain.RefreshTokenSession;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT session FROM RefreshTokenSession session WHERE session.tokenHash = :tokenHash")
	Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
