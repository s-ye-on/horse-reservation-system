package com.horse.schedules.presentation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.schedules.application.RegularScheduleTemplateCommand;
import com.horse.schedules.application.RegularScheduleTemplateService;
import com.horse.schedules.application.ScheduleOccurrenceSynchronizationService;
import com.horse.schedules.application.ScheduleTemplateFutureReservationQueryService;
import com.horse.schedules.application.ScheduleTemplateMutationResult;
import com.horse.schedules.presentation.dto.ScheduleActivationRequest;
import com.horse.schedules.presentation.dto.ScheduleTemplateDeleteRequest;
import com.horse.schedules.presentation.dto.ScheduleTemplateImpactResponse;
import com.horse.schedules.presentation.dto.ScheduleTemplateFutureReservationsResponse;
import com.horse.schedules.presentation.dto.ScheduleTemplateMutationResponse;
import com.horse.schedules.presentation.dto.ScheduleTemplatePreviewRequest;
import com.horse.schedules.presentation.dto.ScheduleTemplateRequest;
import com.horse.schedules.presentation.dto.ScheduleTemplateResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/schedule-templates")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminScheduleTemplateController {

	private final RegularScheduleTemplateService service;
	private final ScheduleOccurrenceSynchronizationService synchronizationService;
	private final ScheduleTemplateFutureReservationQueryService futureReservationQueryService;

	public AdminScheduleTemplateController(
		RegularScheduleTemplateService service,
		ScheduleOccurrenceSynchronizationService synchronizationService,
		ScheduleTemplateFutureReservationQueryService futureReservationQueryService
	) {
		this.service = service;
		this.synchronizationService = synchronizationService;
		this.futureReservationQueryService = futureReservationQueryService;
	}

	@GetMapping
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		array = @ArraySchema(schema = @Schema(implementation = ScheduleTemplateResponse.class))))
	public List<ScheduleTemplateResponse> getTemplates() {
		return service.findAll().stream()
			.map(ScheduleTemplateResponse::from)
			.toList();
	}

	@GetMapping("/{templateId}/future-occupying-reservations")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateFutureReservationsResponse.class)))
	public ScheduleTemplateFutureReservationsResponse getFutureOccupyingReservations(
		@PathVariable long templateId
	) {
		return ScheduleTemplateFutureReservationsResponse.from(
			futureReservationQueryService.find(templateId));
	}

	@PostMapping("/preview")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateImpactResponse.class)))
	public ScheduleTemplateImpactResponse preview(
		@Valid @RequestBody ScheduleTemplatePreviewRequest request
	) {
		return ScheduleTemplateImpactResponse.from(service.preview(
			request.dayOfWeek(),
			request.startTime(),
			request.templateId()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateMutationResponse.class)))
	public ScheduleTemplateMutationResponse create(
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleTemplateRequest request
	) {
		return response(service.create(command(request, adminSubject)));
	}

	@PutMapping("/{templateId}")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateMutationResponse.class)))
	public ScheduleTemplateMutationResponse update(
		@PathVariable long templateId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleTemplateRequest request
	) {
		return response(service.update(templateId, command(request, adminSubject)));
	}

	@DeleteMapping("/{templateId}")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateMutationResponse.class)))
	public ScheduleTemplateMutationResponse delete(
		@PathVariable long templateId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleTemplateDeleteRequest request
	) {
		return response(service.delete(
			templateId,
			request.expectedConfigVersion(),
			adminSubject,
			request.reason()));
	}

	@PatchMapping("/{templateId}/activation")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleTemplateMutationResponse.class)))
	public ScheduleTemplateMutationResponse changeActivation(
		@PathVariable long templateId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody ScheduleActivationRequest request
	) {
		final ScheduleTemplateMutationResult result = request.active()
			? service.activate(
				templateId,
				request.expectedConfigVersion(),
				adminSubject,
				request.reason())
			: service.deactivate(
				templateId,
				request.expectedConfigVersion(),
				adminSubject,
				request.reason());
		return response(result);
	}

	private ScheduleTemplateMutationResponse response(ScheduleTemplateMutationResult result) {
		return ScheduleTemplateMutationResponse.from(
			result,
			synchronizationService.getStatus());
	}

	private RegularScheduleTemplateCommand command(
		ScheduleTemplateRequest request,
		String adminSubject
	) {
		return new RegularScheduleTemplateCommand(
			request.dayOfWeek(),
			request.startTime(),
			request.endTime(),
			request.totalCapacity(),
			request.roundArenaCapacity(),
			request.classCapacities(),
			request.expectedConfigVersion(),
			adminSubject,
			request.reason());
	}
}
