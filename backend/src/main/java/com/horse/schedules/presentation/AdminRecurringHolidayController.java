package com.horse.schedules.presentation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.schedules.application.RecurringHolidayMutationResult;
import com.horse.schedules.application.RecurringHolidayRuleCommand;
import com.horse.schedules.application.RecurringHolidayRuleService;
import com.horse.schedules.application.ScheduleOccurrenceSynchronizationService;
import com.horse.schedules.presentation.dto.RecurringHolidayImpactResponse;
import com.horse.schedules.presentation.dto.RecurringHolidayMutationResponse;
import com.horse.schedules.presentation.dto.RecurringHolidayPreviewRequest;
import com.horse.schedules.presentation.dto.RecurringHolidayRequest;
import com.horse.schedules.presentation.dto.RecurringHolidayResponse;
import com.horse.schedules.presentation.dto.ScheduleActivationRequest;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/recurring-holidays")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminRecurringHolidayController {

	private final RecurringHolidayRuleService service;
	private final ScheduleOccurrenceSynchronizationService synchronizationService;

	public AdminRecurringHolidayController(
		RecurringHolidayRuleService service,
		ScheduleOccurrenceSynchronizationService synchronizationService
	) {
		this.service = service;
		this.synchronizationService = synchronizationService;
	}

	@GetMapping
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		array = @ArraySchema(schema = @Schema(implementation = RecurringHolidayResponse.class))))
	public List<RecurringHolidayResponse> getHolidays() {
		return service.findAll().stream()
			.map(RecurringHolidayResponse::from)
			.toList();
	}

	@PostMapping("/preview")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = RecurringHolidayImpactResponse.class)))
	public RecurringHolidayImpactResponse preview(
		@Valid @RequestBody RecurringHolidayPreviewRequest request
	) {
		return RecurringHolidayImpactResponse.from(service.preview(
			request.holidayId(),
			request.dayOfWeek(),
			request.effectiveFrom(),
			request.effectiveTo()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = RecurringHolidayMutationResponse.class)))
	public RecurringHolidayMutationResponse create(
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody RecurringHolidayRequest request
	) {
		return response(service.create(command(request, adminSubject)));
	}

	@PutMapping("/{holidayId}")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = RecurringHolidayMutationResponse.class)))
	public RecurringHolidayMutationResponse update(
		@PathVariable long holidayId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody RecurringHolidayRequest request
	) {
		return response(service.update(holidayId, command(request, adminSubject)));
	}

	@PatchMapping("/{holidayId}/activation")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = RecurringHolidayMutationResponse.class)))
	public RecurringHolidayMutationResponse changeActivation(
		@PathVariable long holidayId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleActivationRequest request
	) {
		final RecurringHolidayMutationResult result = request.active()
			? service.activate(
				holidayId,
				request.expectedConfigVersion(),
				adminSubject,
				request.reason())
			: service.deactivate(
				holidayId,
				request.expectedConfigVersion(),
				adminSubject,
				request.reason());
		return response(result);
	}

	private RecurringHolidayMutationResponse response(RecurringHolidayMutationResult result) {
		return RecurringHolidayMutationResponse.from(
			result,
			synchronizationService.getStatus());
	}

	private RecurringHolidayRuleCommand command(
		RecurringHolidayRequest request,
		String adminSubject
	) {
		return new RecurringHolidayRuleCommand(
			request.dayOfWeek(),
			request.effectiveFrom(),
			request.effectiveTo(),
			request.holidayReason(),
			request.expectedConfigVersion(),
			adminSubject,
			request.changeReason());
	}
}
