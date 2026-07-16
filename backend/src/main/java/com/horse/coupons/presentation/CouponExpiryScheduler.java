package com.horse.coupons.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.coupons.application.CouponExpiryService;
import com.horse.global.observability.OperationalJobContext;
import com.horse.global.observability.OperationalJobLogger;
import com.horse.global.observability.OperationalJobName;

@Component
public class CouponExpiryScheduler {

	private final CouponExpiryService service;
	private final OperationalJobLogger jobLogger;

	public CouponExpiryScheduler(CouponExpiryService service, OperationalJobLogger jobLogger) {
		this.service = service;
		this.jobLogger = jobLogger;
	}

	@Scheduled(
		fixedDelayString = "${coupon.expiry.fixed-delay}",
		initialDelayString = "${coupon.expiry.initial-delay}"
	)
	public void expireCoupons() {
		jobLogger.execute(
			OperationalJobContext.scheduled(OperationalJobName.COUPON_EXPIRY),
			service::expireDueCoupons,
			result -> "expiredCouponCount=%d,expiredAvailableCount=%d".formatted(
				result.expiredCouponCount(), result.expiredAvailableCount()));
	}
}
