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

	@Query(value = """
		SELECT membership.family_group_id
		FROM family_memberships membership
		JOIN family_groups family_group
		  ON family_group.id = membership.family_group_id
		 AND family_group.status = 'ACTIVE'
		WHERE membership.active_member_guard = :memberId
		LIMIT 1
		""", nativeQuery = true)
	Optional<Long> findActiveGroupIdByMemberId(@Param("memberId") Long memberId);

	@Query(value = """
		SELECT membership.id
		FROM family_memberships membership
		WHERE membership.family_group_id = :groupId
		  AND membership.active_member_guard = :memberId
		FOR UPDATE
		""", nativeQuery = true)
	Optional<Long> findActiveIdByGroupIdAndMemberIdForUpdate(
		@Param("groupId") Long groupId,
		@Param("memberId") Long memberId
	);

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

	@Query(value = """
		SELECT requester.family_group_id
		FROM family_memberships requester
		JOIN family_groups family_group
		  ON family_group.id = requester.family_group_id
		 AND family_group.status = 'ACTIVE'
		JOIN family_memberships coupon_owner
		  ON coupon_owner.family_group_id = requester.family_group_id
		 AND coupon_owner.member_id = :couponOwnerMemberId
		 AND coupon_owner.ended_at IS NULL
		WHERE requester.member_id = :reservationMemberId
		  AND requester.ended_at IS NULL
		LIMIT 1
		""", nativeQuery = true)
	Optional<Long> findSharedActiveGroupId(
		@Param("reservationMemberId") Long reservationMemberId,
		@Param("couponOwnerMemberId") Long couponOwnerMemberId
	);
}
