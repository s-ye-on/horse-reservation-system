package com.horse.auth.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.auth.domain.RefreshTokenSession;
import com.horse.auth.domain.RefreshTokenSessionStatus;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT session FROM RefreshTokenSession session WHERE session.tokenHash = :tokenHash")
	Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT session
		FROM RefreshTokenSession session
		WHERE session.familyId = :familyId
		AND session.status = :status
		ORDER BY session.id
		""")
	List<RefreshTokenSession> findByFamilyIdAndStatusForUpdate(
		@Param("familyId") String familyId,
		@Param("status") RefreshTokenSessionStatus status
	);
}
