package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.AdminReservationPageResult;
import com.horse.reservations.application.AdminReservationQueryService;
import com.horse.reservations.application.AdminReservationResult;
import com.horse.reservations.presentation.dto.AdminReservationPageResponse;
import com.horse.reservations.presentation.dto.AdminReservationQueryRequest;
import com.horse.reservations.presentation.dto.AdminReservationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationQueryController {

	private final AdminReservationQueryService service;

	public AdminReservationQueryController(AdminReservationQueryService service) {
		this.service = service;
	}

	@GetMapping
	public AdminReservationPageResponse getReservations(
		@Valid @ModelAttribute AdminReservationQueryRequest request
	) {
		final AdminReservationPageResult result = service.getReservations(
			request.status(),
			request.lessonDateFrom(),
			request.lessonDateTo(),
			request.classType(),
			request.keyword(),
			request.page(),
			request.size());
		return AdminReservationPageResponse.from(result);
	}

	@GetMapping("/{reservationId}")
	public AdminReservationResponse getReservation(@PathVariable Long reservationId) {
		final AdminReservationResult result = service.getReservation(reservationId);
		return AdminReservationResponse.from(result);
	}
}
