package com.horse.coupons.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.coupons.application.CouponExpiryService;

@Component
public class CouponExpiryScheduler {

	private final CouponExpiryService service;

	public CouponExpiryScheduler(CouponExpiryService service) {
		this.service = service;
	}

	@Scheduled(
		fixedDelayString = "${coupon.expiry.fixed-delay}",
		initialDelayString = "${coupon.expiry.initial-delay}"
	)
	public void expireCoupons() {
		service.expireDueCoupons();
	}
}
