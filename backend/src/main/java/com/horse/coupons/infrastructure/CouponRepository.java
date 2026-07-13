package com.horse.coupons.infrastructure;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.coupons.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

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
}
