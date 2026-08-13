package com.horse.families.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.families.domain.FamilyMembership;

public interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT membership
		FROM FamilyMembership membership
		WHERE membership.member.id = :memberId
		  AND membership.endedAt IS NULL
		""")
	Optional<FamilyMembership> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT membership
		FROM FamilyMembership membership
		WHERE membership.familyGroup.id = :groupId
		  AND membership.member.id = :memberId
		  AND membership.endedAt IS NULL
		""")
	Optional<FamilyMembership> findActiveByGroupIdAndMemberIdForUpdate(
		@Param("groupId") Long groupId,
		@Param("memberId") Long memberId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT membership
		FROM FamilyMembership membership
		WHERE membership.familyGroup.id = :groupId
		  AND membership.endedAt IS NULL
		ORDER BY membership.member.id, membership.id
		""")
	List<FamilyMembership> findAllActiveByGroupIdForUpdate(@Param("groupId") Long groupId);
}
