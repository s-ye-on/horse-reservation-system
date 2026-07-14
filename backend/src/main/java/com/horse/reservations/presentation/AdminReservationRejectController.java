package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationRejectResult;
import com.horse.reservations.application.ReservationRejectService;
import com.horse.reservations.presentation.dto.ReservationRejectRequest;
import com.horse.reservations.presentation.dto.ReservationRejectResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationRejectController {

	private final ReservationRejectService service;

	public AdminReservationRejectController(ReservationRejectService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/reject")
	public ReservationRejectResponse reject(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ReservationRejectRequest request
	) {
		final ReservationRejectResult result = service.reject(reservationId, adminSubject, request.reason());
		return ReservationRejectResponse.from(result);
	}
}
