package com.horse.reservations.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.global.observability.OperationalJobContext;
import com.horse.global.observability.OperationalJobLogger;
import com.horse.global.observability.OperationalJobName;
import com.horse.reservations.application.ApprovalExpiryService;

@Component
public class ApprovalExpiryScheduler {

	private final ApprovalExpiryService service;
	private final OperationalJobLogger jobLogger;

	public ApprovalExpiryScheduler(ApprovalExpiryService service, OperationalJobLogger jobLogger) {
		this.service = service;
		this.jobLogger = jobLogger;
	}

	@Scheduled(
		fixedDelayString = "${reservation.pending-approval-expiry.fixed-delay}",
		initialDelayString = "${reservation.pending-approval-expiry.initial-delay}"
	)
	public void expirePendingApprovals() {
		jobLogger.execute(
			OperationalJobContext.scheduled(OperationalJobName.PENDING_APPROVAL_EXPIRY),
			service::expireDueApprovals,
			result -> "expiredCount=%d,executedAt=%s".formatted(
				result.expiredCount(), result.executedAt()));
	}
}
