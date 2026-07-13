package com.horse.coupons.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.coupons.domain.CouponUsageAction;
import com.horse.coupons.domain.CouponUsageLog;

public interface CouponUsageLogRepository extends JpaRepository<CouponUsageLog, Long> {

	Optional<CouponUsageLog> findFirstByReservationIdAndActionOrderByIdAsc(
		Long reservationId,
		CouponUsageAction action
	);

	boolean existsByReservationIdAndAction(Long reservationId, CouponUsageAction action);
}
