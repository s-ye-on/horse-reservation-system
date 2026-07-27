package com.horse.timeslots.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationCancelResult;
import com.horse.reservations.application.TimeSlotClosureCancellationService;
import com.horse.reservations.presentation.dto.ReservationCancelResponse;
import com.horse.schedules.presentation.dto.ScheduleClosureCancelRequest;
import com.horse.timeslots.application.TimeSlotClosureAdministrationService;
import com.horse.timeslots.presentation.dto.TimeSlotClosureActionRequest;
import com.horse.timeslots.presentation.dto.TimeSlotClosureResponse;
import com.horse.timeslots.presentation.dto.TimeSlotClosureStartRequest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/timeslots/{timeSlotId}")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminTimeSlotClosureController {

	private final TimeSlotClosureAdministrationService service;
	private final TimeSlotClosureCancellationService cancellationService;

	public AdminTimeSlotClosureController(
		TimeSlotClosureAdministrationService service,
		TimeSlotClosureCancellationService cancellationService
	) {
		this.service = service;
		this.cancellationService = cancellationService;
	}

	@GetMapping("/closure-impact")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = TimeSlotClosureResponse.class)))
	public TimeSlotClosureResponse getClosure(@PathVariable long timeSlotId) {
		return TimeSlotClosureResponse.from(service.findLatest(timeSlotId));
	}

	@PostMapping("/closure")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = TimeSlotClosureResponse.class)))
	public TimeSlotClosureResponse start(
		@PathVariable long timeSlotId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody TimeSlotClosureStartRequest request
	) {
		return TimeSlotClosureResponse.from(
			service.start(timeSlotId, adminSubject, request.reason()));
	}

	@PostMapping("/closure/complete")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = TimeSlotClosureResponse.class)))
	public TimeSlotClosureResponse complete(
		@PathVariable long timeSlotId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody TimeSlotClosureActionRequest request
	) {
		return TimeSlotClosureResponse.from(service.complete(
			timeSlotId,
			adminSubject,
			request.reason(),
			request.expectedVersion()));
	}

	@PostMapping("/closure/withdraw")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = TimeSlotClosureResponse.class)))
	public TimeSlotClosureResponse withdraw(
		@PathVariable long timeSlotId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody TimeSlotClosureActionRequest request
	) {
		return TimeSlotClosureResponse.from(service.withdraw(
			timeSlotId,
			adminSubject,
			request.reason(),
			request.expectedVersion()));
	}

	@PostMapping("/closure/reopen")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = TimeSlotClosureResponse.class)))
	public TimeSlotClosureResponse reopen(
		@PathVariable long timeSlotId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody TimeSlotClosureActionRequest request
	) {
		return TimeSlotClosureResponse.from(service.reopen(
			timeSlotId,
			adminSubject,
			request.reason(),
			request.expectedVersion()));
	}

	@PostMapping("/reservations/{reservationId}/cancel-for-closure")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ReservationCancelResponse.class)))
	public ReservationCancelResponse cancelReservation(
		@PathVariable long timeSlotId,
		@PathVariable long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleClosureCancelRequest request
	) {
		final ReservationCancelResult result = cancellationService.cancelByAdmin(
			timeSlotId,
			reservationId,
			adminSubject,
			request.memo());
		return ReservationCancelResponse.from(result);
	}

}
