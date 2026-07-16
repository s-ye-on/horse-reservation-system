package com.horse.reservations.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.global.observability.OperationalJobContext;
import com.horse.global.observability.OperationalJobLogger;
import com.horse.global.observability.OperationalJobName;
import com.horse.reservations.application.PendingPaymentExpiryService;

@Component
public class PendingPaymentExpiryScheduler {

	private final PendingPaymentExpiryService service;
	private final OperationalJobLogger jobLogger;

	public PendingPaymentExpiryScheduler(PendingPaymentExpiryService service, OperationalJobLogger jobLogger) {
		this.service = service;
		this.jobLogger = jobLogger;
	}

	@Scheduled(
		fixedDelayString = "${reservation.pending-payment-expiry.fixed-delay}",
		initialDelayString = "${reservation.pending-payment-expiry.initial-delay}"
	)
	public void expirePendingPayments() {
		jobLogger.execute(
			OperationalJobContext.scheduled(OperationalJobName.PENDING_PAYMENT_EXPIRY),
			service::expireDuePayments,
			result -> "expiredCount=%d,executedAt=%s".formatted(
				result.expiredCount(), result.executedAt()));
	}
}
