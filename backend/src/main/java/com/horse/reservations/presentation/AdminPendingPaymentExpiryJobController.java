package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.global.observability.OperationalJobContext;
import com.horse.global.observability.OperationalJobLogger;
import com.horse.global.observability.OperationalJobName;
import com.horse.reservations.application.PendingPaymentExpiryResult;
import com.horse.reservations.application.PendingPaymentExpiryService;
import com.horse.reservations.presentation.dto.PendingPaymentExpiryResponse;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminPendingPaymentExpiryJobController {

	private final PendingPaymentExpiryService service;
	private final OperationalJobLogger jobLogger;

	public AdminPendingPaymentExpiryJobController(
		PendingPaymentExpiryService service,
		OperationalJobLogger jobLogger
	) {
		this.service = service;
		this.jobLogger = jobLogger;
	}

	@PostMapping("/expire-pending-payments")
	public PendingPaymentExpiryResponse expirePendingPayments(
		@AuthenticationPrincipal(expression = "subject") String adminSubject
	) {
		final PendingPaymentExpiryResult result = jobLogger.execute(
			OperationalJobContext.manual(OperationalJobName.PENDING_PAYMENT_EXPIRY, adminSubject),
			service::expireDuePayments,
			value -> "expiredCount=%d,executedAt=%s".formatted(
				value.expiredCount(), value.executedAt()));
		return PendingPaymentExpiryResponse.from(result);
	}
}
