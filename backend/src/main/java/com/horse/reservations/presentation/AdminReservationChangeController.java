package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationChangeService;
import com.horse.reservations.presentation.dto.AdminReservationChangeRequest;
import com.horse.reservations.presentation.dto.ReservationChangeResponse;

@RestController
@RequestMapping("/api/admin/reservations/{reservationId}/change")
public class AdminReservationChangeController {

	private final ReservationChangeService service;

	public AdminReservationChangeController(ReservationChangeService service) {
		this.service = service;
	}

	@PostMapping
	public ReservationChangeResponse change(
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@PathVariable Long reservationId,
		@RequestBody AdminReservationChangeRequest request
	) {
		return ReservationChangeResponse.from(service.changeByAdmin(
			adminSubject,
			reservationId,
			request.targetTimeSlotId(),
			request.memo()));
	}
}
