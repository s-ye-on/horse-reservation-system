package com.horse.families.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.families.domain.FamilyGroup;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

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
