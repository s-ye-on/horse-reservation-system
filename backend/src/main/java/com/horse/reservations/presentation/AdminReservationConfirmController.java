package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationConfirmResult;
import com.horse.reservations.application.ReservationConfirmService;
import com.horse.reservations.presentation.dto.ReservationConfirmResponse;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationConfirmController {

	private final ReservationConfirmService service;

	public AdminReservationConfirmController(ReservationConfirmService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/confirm")
	public ReservationConfirmResponse confirm(@PathVariable Long reservationId) {
		final ReservationConfirmResult result = service.confirm(reservationId);
		return ReservationConfirmResponse.from(result);
	}
}
