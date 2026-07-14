package com.horse.reservations.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.AdminPendingPaymentQueryService;
import com.horse.reservations.presentation.dto.AdminPendingPaymentResponse;

@RestController
@RequestMapping("/api/admin/pending-payments")
public class AdminPendingPaymentQueryController {

	private final AdminPendingPaymentQueryService service;

	public AdminPendingPaymentQueryController(AdminPendingPaymentQueryService service) {
		this.service = service;
	}

	@GetMapping
	public List<AdminPendingPaymentResponse> getPendingPayments() {
		return service.getPendingPayments().stream()
			.map(AdminPendingPaymentResponse::from)
			.toList();
	}
}
