package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationNoShowResult;
import com.horse.reservations.application.ReservationNoShowService;
import com.horse.reservations.presentation.dto.ReservationNoShowRequest;
import com.horse.reservations.presentation.dto.ReservationNoShowResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationNoShowController {

	private final ReservationNoShowService service;

	public AdminReservationNoShowController(ReservationNoShowService service) {
		this.service = service;
	}

	@PostMapping("/{reservationId}/no-show")
	public ReservationNoShowResponse process(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ReservationNoShowRequest request
	) {
		final ReservationNoShowResult result = service.process(
			reservationId,
			adminSubject,
			request.couponAction(),
			request.memo());
		return ReservationNoShowResponse.from(result);
	}
}
