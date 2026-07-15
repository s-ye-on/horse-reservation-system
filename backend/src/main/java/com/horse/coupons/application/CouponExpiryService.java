package com.horse.coupons.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponUsageLog;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;

@Service
public class CouponExpiryService {

	private final Clock clock;
	private final CouponRepository couponRepository;
	private final CouponUsageLogRepository usageLogRepository;

	public CouponExpiryService(
		Clock clock,
		CouponRepository couponRepository,
		CouponUsageLogRepository usageLogRepository
	) {
		this.clock = clock;
		this.couponRepository = couponRepository;
		this.usageLogRepository = usageLogRepository;
	}

	@Transactional
	public CouponExpiryResult expireDueCoupons() {
		final LocalDate today = LocalDate.now(clock);
		final LocalDateTime occurredAt = LocalDateTime.now(clock);
		final List<Coupon> dueCoupons = couponRepository.findDueForExpiryForUpdate(today.atStartOfDay());
		int expiredAvailableCount = 0;
		for (Coupon coupon : dueCoupons) {
			final int expiredCount = coupon.expire(today);
			expiredAvailableCount += expiredCount;
			usageLogRepository.save(CouponUsageLog.expired(
				coupon.getId(), coupon.getMemberId(), expiredCount, occurredAt));
		}
		return new CouponExpiryResult(dueCoupons.size(), expiredAvailableCount);
	}
}
