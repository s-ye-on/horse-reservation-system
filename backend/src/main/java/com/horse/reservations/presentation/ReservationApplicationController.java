package com.horse.reservations.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationApplicationResult;
import com.horse.reservations.application.ReservationApplicationService;
import com.horse.reservations.presentation.dto.ReservationApplicationRequest;
import com.horse.reservations.presentation.dto.ReservationApplicationResponse;

@RestController
@RequestMapping("/api/reservations")
public class ReservationApplicationController {

	private final ReservationApplicationService service;

	public ReservationApplicationController(ReservationApplicationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationApplicationResponse apply(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@RequestBody ReservationApplicationRequest request
	) {
		final ReservationApplicationResult result = service.apply(
			authSubject,
			request.timeSlotId(),
			request.classType());
		return ReservationApplicationResponse.from(result);
	}
}
