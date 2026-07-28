package com.horse.reservations.presentation;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.IdempotentReservationApplicationResult;
import com.horse.reservations.application.IdempotentReservationApplicationService;
import com.horse.reservations.presentation.dto.AdminManualReservationRequest;
import com.horse.reservations.presentation.dto.ReservationApplicationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminManualReservationController {

	private final IdempotentReservationApplicationService service;

	public AdminManualReservationController(IdempotentReservationApplicationService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(operationId = "createAdminManualReservation")
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = ReservationApplicationResponse.class)))
	public ResponseEntity<String> create(
		@AuthenticationPrincipal(expression = "subject") String adminAuthSubject,
		@Parameter(
			required = true,
			description = "관리자의 수동 예약 생성 요청을 식별하는 멱등성 키")
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody AdminManualReservationRequest request
	) {
		final IdempotentReservationApplicationResult result = service.applyByAdmin(
			adminAuthSubject,
			idempotencyKey,
			request.memberId(),
			request.timeSlotId(),
			request.classType(),
			request.reason());
		return ResponseEntity.status(result.httpStatus())
			.contentType(MediaType.APPLICATION_JSON)
			.body(result.responseBody());
	}
}
