package com.horse.schedules.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.schedules.application.ScheduleOccurrenceSynchronizationService;
import com.horse.schedules.presentation.dto.ScheduleSynchronizationResponse;
import com.horse.schedules.presentation.dto.ScheduleSynchronizationResultResponse;
import com.horse.schedules.presentation.dto.ScheduleSynchronizationRetryRequest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminScheduleSynchronizationController {

	private final ScheduleOccurrenceSynchronizationService service;

	public AdminScheduleSynchronizationController(
		ScheduleOccurrenceSynchronizationService service
	) {
		this.service = service;
	}

	@GetMapping("/schedule-sync")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleSynchronizationResponse.class)))
	public ScheduleSynchronizationResponse getStatus() {
		return ScheduleSynchronizationResponse.from(service.getStatus());
	}

	@PostMapping("/jobs/sync-schedule-occurrences/retry")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = ScheduleSynchronizationResultResponse.class)))
	public ScheduleSynchronizationResultResponse retry(
		@Valid @RequestBody ScheduleSynchronizationRetryRequest request
	) {
		return ScheduleSynchronizationResultResponse.from(
			service.retryManualSynchronization(request.pendingVersion()));
	}
}
