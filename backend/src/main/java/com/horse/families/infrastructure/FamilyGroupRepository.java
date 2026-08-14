package com.horse.families.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.families.domain.FamilyGroup;
import com.horse.families.domain.FamilyGroupStatus;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

	@Query("""
		SELECT familyGroup
		FROM FamilyGroup familyGroup
		WHERE (:query = '' OR LOWER(familyGroup.name) LIKE LOWER(CONCAT('%', :query, '%')))
		  AND (:status IS NULL OR familyGroup.status = :status)
		""")
	Page<FamilyGroup> search(
		@Param("query") String query,
		@Param("status") FamilyGroupStatus status,
		Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT familyGroup FROM FamilyGroup familyGroup WHERE familyGroup.id = :groupId")
	Optional<FamilyGroup> findByIdForUpdate(@Param("groupId") Long groupId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT familyGroup
		FROM FamilyGroup familyGroup
		WHERE familyGroup.id = :groupId
		  AND familyGroup.status = com.horse.families.domain.FamilyGroupStatus.ACTIVE
		""")
	Optional<FamilyGroup> findActiveByIdForUpdate(@Param("groupId") Long groupId);
}
