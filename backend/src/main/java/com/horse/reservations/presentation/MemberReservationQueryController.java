package com.horse.reservations.presentation;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.MemberReservationQueryService;
import com.horse.reservations.presentation.dto.MemberReservationResponse;

@RestController
@RequestMapping("/api/me/reservations")
public class MemberReservationQueryController {

	private final MemberReservationQueryService service;

	public MemberReservationQueryController(MemberReservationQueryService service) {
		this.service = service;
	}

	@GetMapping
	public List<MemberReservationResponse> getMyReservations(
		@AuthenticationPrincipal(expression = "subject") String authSubject
	) {
		return service.getMyReservations(authSubject).stream()
			.map(MemberReservationResponse::from)
			.toList();
	}
}
