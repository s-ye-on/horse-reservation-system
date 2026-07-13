package com.horse.coupons.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.coupons.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
