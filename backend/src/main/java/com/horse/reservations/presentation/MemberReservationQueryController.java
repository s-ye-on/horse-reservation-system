package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import com.horse.reservations.application.MemberReservationPageResult;
import com.horse.reservations.application.MemberReservationQueryService;
import com.horse.reservations.application.MemberReservationResult;
import com.horse.reservations.presentation.dto.MemberReservationPageResponse;
import com.horse.reservations.presentation.dto.MemberReservationQueryRequest;
import com.horse.reservations.presentation.dto.MemberReservationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/reservations")
public class MemberReservationQueryController {

	private final MemberReservationQueryService service;

	public MemberReservationQueryController(MemberReservationQueryService service) {
		this.service = service;
	}

	@GetMapping
	public MemberReservationPageResponse getMyReservations(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@Valid @ParameterObject @ModelAttribute MemberReservationQueryRequest request
	) {
		final MemberReservationPageResult result = service.getMyReservations(
			authSubject,
			request.page(),
			request.size());
		return MemberReservationPageResponse.from(result);
	}

	@GetMapping("/{reservationId}")
	public MemberReservationResponse getMyReservation(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@PathVariable Long reservationId
	) {
		final MemberReservationResult result = service.getMyReservation(authSubject, reservationId);
		return MemberReservationResponse.from(result);
	}
}
