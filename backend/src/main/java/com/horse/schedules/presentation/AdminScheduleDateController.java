package com.horse.schedules.presentation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.ReservationCancelResult;
import com.horse.reservations.application.ScheduleDateClosureCancellationService;
import com.horse.reservations.presentation.dto.ReservationCancelResponse;
import com.horse.schedules.application.ScheduleDateAdministrationResult;
import com.horse.schedules.application.ScheduleDateAdministrationService;
import com.horse.schedules.application.ScheduleDateQueryService;
import com.horse.schedules.presentation.dto.ScheduleClosureCancelRequest;
import com.horse.schedules.presentation.dto.ScheduleDateActionRequest;
import com.horse.schedules.presentation.dto.ScheduleDateClosureImpactResponse;
import com.horse.schedules.presentation.dto.ScheduleDateResponse;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/schedule-dates")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminScheduleDateController {

	private final ScheduleDateQueryService queryService;
	private final ScheduleDateAdministrationService administrationService;
	private final ScheduleDateClosureCancellationService cancellationService;

	public AdminScheduleDateController(
		ScheduleDateQueryService queryService,
		ScheduleDateAdministrationService administrationService,
		ScheduleDateClosureCancellationService cancellationService
	) {
		this.queryService = queryService;
		this.administrationService = administrationService;
		this.cancellationService = cancellationService;
	}

	@GetMapping
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		array = @ArraySchema(schema = @Schema(implementation = ScheduleDateResponse.class))))
	public List<ScheduleDateResponse> getScheduleDates(
		@RequestParam LocalDate dateFrom,
		@RequestParam LocalDate dateTo
	) {
		return queryService.findAll(dateFrom, dateTo).stream()
			.map(ScheduleDateResponse::from)
			.toList();
	}

	@GetMapping("/{scheduleDate}")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleDateResponse.class)))
	public ScheduleDateResponse getScheduleDate(@PathVariable LocalDate scheduleDate) {
		return ScheduleDateResponse.from(queryService.find(scheduleDate));
	}

	@GetMapping("/{scheduleDate}/closure-impact")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleDateClosureImpactResponse.class)))
	public ScheduleDateClosureImpactResponse getClosureImpact(
		@PathVariable LocalDate scheduleDate
	) {
		return response(administrationService.getImpact(scheduleDate));
	}

	@PostMapping("/{scheduleDate}/closing")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleDateClosureImpactResponse.class)))
	public ScheduleDateClosureImpactResponse startClosing(
		@PathVariable LocalDate scheduleDate,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleDateActionRequest request
	) {
		return response(
			administrationService.startClosing(
				scheduleDate,
				adminSubject,
				request.reason(),
				request.expectedVersion()));
	}

	@PostMapping("/{scheduleDate}/close")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleDateClosureImpactResponse.class)))
	public ScheduleDateClosureImpactResponse close(
		@PathVariable LocalDate scheduleDate,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleDateActionRequest request
	) {
		return response(
			administrationService.close(
				scheduleDate,
				adminSubject,
				request.reason(),
				request.expectedVersion()));
	}

	@PostMapping("/{scheduleDate}/closing/cancel")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleDateClosureImpactResponse.class)))
	public ScheduleDateClosureImpactResponse cancelClosing(
		@PathVariable LocalDate scheduleDate,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleDateActionRequest request
	) {
		return response(
			administrationService.cancelClosing(
				scheduleDate,
				adminSubject,
				request.reason(),
				request.expectedVersion()));
	}

	@PostMapping("/{scheduleDate}/reservations/{reservationId}/cancel-for-closure")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ReservationCancelResponse.class)))
	public ReservationCancelResponse cancelReservation(
		@PathVariable LocalDate scheduleDate,
		@PathVariable long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleClosureCancelRequest request
	) {
		final ReservationCancelResult result = cancellationService.cancel(
			scheduleDate,
			reservationId,
			adminSubject,
			request.memo());
		return ReservationCancelResponse.from(result);
	}

	private ScheduleDateClosureImpactResponse response(
		ScheduleDateAdministrationResult result
	) {
		return ScheduleDateClosureImpactResponse.from(
			result.closure(),
			result.scheduleDate(),
			result.reservations(),
			result.initialReservationCount());
	}
}
