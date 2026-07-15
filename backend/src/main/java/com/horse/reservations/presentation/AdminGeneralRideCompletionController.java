package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.GeneralRideCompletionResult;
import com.horse.reservations.application.GeneralRideCompletionService;
import com.horse.reservations.presentation.dto.GeneralRideCompletionResponse;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminGeneralRideCompletionController {

	private final GeneralRideCompletionService service;

	public AdminGeneralRideCompletionController(GeneralRideCompletionService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/complete")
	public GeneralRideCompletionResponse complete(@PathVariable Long reservationId) {
		final GeneralRideCompletionResult result = service.complete(reservationId);
		return GeneralRideCompletionResponse.from(result);
	}
}
