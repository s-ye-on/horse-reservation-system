package com.horse.coupons.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.coupons.domain.Coupon;

import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

	List<Coupon> findAllByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

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
		SELECT c.*
		FROM coupons c
		WHERE c.member_id = :memberId
			AND c.coupon_type = :couponType
			AND c.status = 'active'
			AND c.remaining_count > c.held_count
			AND (c.expires_at IS NULL OR DATE(c.expires_at) >= :lessonDate)
		ORDER BY (c.expires_at IS NULL) ASC,
			c.expires_at ASC,
			c.created_at ASC,
			c.id ASC
		LIMIT 1
		""", nativeQuery = true)
	Optional<Coupon> findFirstSelectable(
		@Param("memberId") Long memberId,
		@Param("couponType") String couponType,
		@Param("lessonDate") LocalDate lessonDate
	);

	@Query(value = """
		SELECT c.*
		FROM coupons c
		WHERE c.member_id = :memberId
			AND c.coupon_type = :couponType
			AND c.status = 'active'
			AND c.remaining_count > c.held_count
			AND (c.expires_at IS NULL OR DATE(c.expires_at) >= :lessonDate)
		ORDER BY (c.expires_at IS NULL) ASC,
			c.expires_at ASC,
			c.created_at ASC,
			c.id ASC
		LIMIT 1
		FOR UPDATE
		""", nativeQuery = true)
	Optional<Coupon> findFirstSelectableForUpdate(
		@Param("memberId") Long memberId,
		@Param("couponType") String couponType,
		@Param("lessonDate") LocalDate lessonDate
	);
}
