package com.horse.reservations.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.reservations.application.PendingPaymentExpiryService;

@Component
public class PendingPaymentExpiryScheduler {

	private final PendingPaymentExpiryService service;

	public PendingPaymentExpiryScheduler(PendingPaymentExpiryService service) {
		this.service = service;
	}

	@Scheduled(
		fixedDelayString = "${reservation.pending-payment-expiry.fixed-delay}",
		initialDelayString = "${reservation.pending-payment-expiry.initial-delay}"
	)
	public void expirePendingPayments() {
		service.expireDuePayments();
	}
}
