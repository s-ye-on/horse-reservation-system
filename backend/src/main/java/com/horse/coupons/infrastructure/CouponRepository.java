package com.horse.coupons.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.coupons.domain.Coupon;

import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

	Page<Coupon> findAllByMemberId(Long memberId, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Coupon c WHERE c.id = :couponId")
	Optional<Coupon> findByIdForUpdate(@Param("couponId") Long couponId);

	@Query(value = """
		SELECT c.*
		FROM coupons c
		WHERE c.status = 'active'
			AND c.expires_at < :todayStart
		ORDER BY c.id ASC
		FOR UPDATE
		""", nativeQuery = true)
	List<Coupon> findDueForExpiryForUpdate(@Param("todayStart") LocalDateTime todayStart);

	@Query(value = """
		SELECT c.id
		FROM coupons c
		WHERE c.coupon_type = :couponType
			AND c.status = 'active'
			AND c.remaining_count > c.held_count
			AND (c.expires_at IS NULL OR DATE(c.expires_at) >= :lessonDate)
			AND (
				c.member_id = :memberId
				OR EXISTS (
					SELECT 1
					FROM family_memberships requester
					JOIN family_groups family_group
					  ON family_group.id = requester.family_group_id
					 AND family_group.status = 'ACTIVE'
					JOIN family_memberships coupon_owner
					  ON coupon_owner.family_group_id = requester.family_group_id
					 AND coupon_owner.member_id = c.member_id
					 AND coupon_owner.ended_at IS NULL
					WHERE requester.member_id = :memberId
					  AND requester.ended_at IS NULL
				)
			)
		ORDER BY c.expiry_null_rank ASC,
			c.expires_at ASC,
			c.created_at ASC,
			c.id ASC
		LIMIT 1
		""", nativeQuery = true)
	Optional<Long> findFirstSelectableId(
		@Param("memberId") Long memberId,
		@Param("couponType") String couponType,
		@Param("lessonDate") LocalDate lessonDate
	);

	@Query(value = """
		SELECT locked_coupon.id
		FROM coupons locked_coupon
		JOIN (
			SELECT candidate.id
			FROM coupons candidate
			LEFT JOIN family_memberships coupon_owner
			  ON coupon_owner.active_member_guard = candidate.member_id
			 AND coupon_owner.family_group_id = :familyGroupId
			WHERE candidate.coupon_type = :couponType
				AND candidate.status = 'active'
				AND candidate.remaining_count > candidate.held_count
				AND (candidate.expires_at IS NULL OR DATE(candidate.expires_at) >= :lessonDate)
				AND (candidate.member_id = :memberId OR coupon_owner.id IS NOT NULL)
			ORDER BY candidate.expiry_null_rank ASC,
				candidate.expires_at ASC,
				candidate.created_at ASC,
				candidate.id ASC
			LIMIT 1
		) selected_coupon ON selected_coupon.id = locked_coupon.id
		WHERE locked_coupon.coupon_type = :couponType
			AND locked_coupon.status = 'active'
			AND locked_coupon.remaining_count > locked_coupon.held_count
			AND (locked_coupon.expires_at IS NULL OR DATE(locked_coupon.expires_at) >= :lessonDate)
		FOR UPDATE
		""", nativeQuery = true)
	Optional<Long> findFirstSelectableIdForUpdate(
		@Param("memberId") Long memberId,
		@Param("familyGroupId") Long familyGroupId,
		@Param("couponType") String couponType,
		@Param("lessonDate") LocalDate lessonDate
	);
}
