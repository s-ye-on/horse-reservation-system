package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationChangeService;
import com.horse.reservations.presentation.dto.MemberReservationChangeRequest;
import com.horse.reservations.presentation.dto.ReservationChangePreviewResponse;
import com.horse.reservations.presentation.dto.ReservationChangeResponse;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/me/reservations/{reservationId}/change")
public class MemberReservationChangeController {

	private final ReservationChangeService service;

	public MemberReservationChangeController(ReservationChangeService service) {
		this.service = service;
	}

	@GetMapping("/preview")
	@Operation(operationId = "previewReservationChange")
	public ReservationChangePreviewResponse preview(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@PathVariable Long reservationId,
		@RequestParam Long targetTimeSlotId
	) {
		return ReservationChangePreviewResponse.from(service.previewByMember(
			authSubject,
			reservationId,
			targetTimeSlotId));
	}

	@PostMapping
	public ReservationChangeResponse change(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@PathVariable Long reservationId,
		@RequestBody MemberReservationChangeRequest request
	) {
		return ReservationChangeResponse.from(service.changeByMember(
			authSubject,
			reservationId,
			request.targetTimeSlotId(),
			request.reason()));
	}
}
