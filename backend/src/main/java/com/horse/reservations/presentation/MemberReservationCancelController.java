package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.MemberReservationCancellationService;
import com.horse.reservations.presentation.dto.MemberReservationCancelRequest;
import com.horse.reservations.presentation.dto.ReservationCancellationPreviewResponse;
import com.horse.reservations.presentation.dto.ReservationCancelResponse;

@RestController
@RequestMapping("/api/me/reservations/{reservationId}")
public class MemberReservationCancelController {

	private final MemberReservationCancellationService service;

	public MemberReservationCancelController(MemberReservationCancellationService service) {
		this.service = service;
	}

	@GetMapping("/cancellation-preview")
	public ReservationCancellationPreviewResponse preview(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@PathVariable Long reservationId
	) {
		return ReservationCancellationPreviewResponse.from(service.preview(authSubject, reservationId));
	}

	@PostMapping("/cancel")
	public ReservationCancelResponse cancel(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@PathVariable Long reservationId,
		@RequestBody MemberReservationCancelRequest request
	) {
		return ReservationCancelResponse.from(service.cancel(
			authSubject,
			reservationId,
			request.reason()));
	}
}
