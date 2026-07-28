package com.horse.reservations.presentation;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.IdempotentReservationApplicationResult;
import com.horse.reservations.application.IdempotentReservationApplicationService;
import com.horse.reservations.presentation.dto.ReservationApplicationRequest;
import com.horse.reservations.presentation.dto.ReservationApplicationResponse;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/reservations")
public class ReservationApplicationController {

	private final IdempotentReservationApplicationService service;

	public ReservationApplicationController(IdempotentReservationApplicationService service) {
		this.service = service;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ReservationApplicationResponse.class)))
	public ResponseEntity<String> apply(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@Parameter(
			required = true,
			description = "인증 주체의 회원 예약 생성 요청을 식별하는 멱등성 키")
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody ReservationApplicationRequest request
	) {
		final IdempotentReservationApplicationResult result = service.apply(
			authSubject,
			idempotencyKey,
			request.timeSlotId(),
			request.classType());
		return ResponseEntity.status(result.httpStatus())
			.contentType(MediaType.APPLICATION_JSON)
			.body(result.responseBody());
	}
}
