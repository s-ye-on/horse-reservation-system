package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.PendingPaymentExpiryResult;
import com.horse.reservations.application.PendingPaymentExpiryService;
import com.horse.reservations.presentation.dto.PendingPaymentExpiryResponse;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminPendingPaymentExpiryJobController {

	private final PendingPaymentExpiryService service;

	public AdminPendingPaymentExpiryJobController(PendingPaymentExpiryService service) {
		this.service = service;
	}

	@PostMapping("/expire-pending-payments")
	public PendingPaymentExpiryResponse expirePendingPayments() {
		final PendingPaymentExpiryResult result = service.expireDuePayments();
		return PendingPaymentExpiryResponse.from(result);
	}
}
