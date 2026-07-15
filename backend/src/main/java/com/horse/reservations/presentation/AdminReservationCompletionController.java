package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationCompletionResult;
import com.horse.reservations.application.ReservationCompletionService;
import com.horse.reservations.presentation.dto.ReservationCompletionResponse;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationCompletionController {

	private final ReservationCompletionService service;

	public AdminReservationCompletionController(ReservationCompletionService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/complete")
	public ReservationCompletionResponse complete(@PathVariable Long reservationId) {
		final ReservationCompletionResult result = service.complete(reservationId);
		return ReservationCompletionResponse.from(result);
	}
}
