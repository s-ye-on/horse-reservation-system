package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationPaymentRestoreResult;
import com.horse.reservations.application.ReservationPaymentRestoreService;
import com.horse.reservations.presentation.dto.ReservationPaymentRestoreRequest;
import com.horse.reservations.presentation.dto.ReservationPaymentRestoreResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminPendingPaymentRestoreController {

	private final ReservationPaymentRestoreService service;

	public AdminPendingPaymentRestoreController(ReservationPaymentRestoreService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/restore-payment")
	public ReservationPaymentRestoreResponse restore(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ReservationPaymentRestoreRequest request
	) {
		final ReservationPaymentRestoreResult result = service.restore(
			reservationId, adminSubject, request.memo());
		return ReservationPaymentRestoreResponse.from(result);
	}
}
