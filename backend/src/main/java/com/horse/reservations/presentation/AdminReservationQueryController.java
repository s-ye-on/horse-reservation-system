package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import com.horse.reservations.application.AdminReservationPageResult;
import com.horse.reservations.application.AdminReservationQueryService;
import com.horse.reservations.application.AdminReservationResult;
import com.horse.reservations.application.AdminReservationSummaryResult;
import com.horse.reservations.application.AdminReservationSummaryService;
import com.horse.reservations.presentation.dto.AdminReservationPageResponse;
import com.horse.reservations.presentation.dto.AdminReservationQueryRequest;
import com.horse.reservations.presentation.dto.AdminReservationResponse;
import com.horse.reservations.presentation.dto.AdminReservationSummaryRequest;
import com.horse.reservations.presentation.dto.AdminReservationSummaryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationQueryController {

	private final AdminReservationQueryService service;
	private final AdminReservationSummaryService summaryService;

	public AdminReservationQueryController(
		AdminReservationQueryService service,
		AdminReservationSummaryService summaryService
	) {
		this.service = service;
		this.summaryService = summaryService;
	}

	@GetMapping("/summary")
	public AdminReservationSummaryResponse getSummary(
		@ParameterObject @ModelAttribute AdminReservationSummaryRequest request
	) {
		final AdminReservationSummaryResult result = summaryService.getSummary(
			request.lessonDateFrom(),
			request.lessonDateTo());
		return AdminReservationSummaryResponse.from(result);
	}

	@GetMapping
	public AdminReservationPageResponse getReservations(
		@Valid @ParameterObject @ModelAttribute AdminReservationQueryRequest request
	) {
		final AdminReservationPageResult result = service.getReservations(
			request.status(),
			request.lessonDateFrom(),
			request.lessonDateTo(),
			request.classType(),
			request.keyword(),
			request.sort(),
			request.page(),
			request.size());
		return AdminReservationPageResponse.from(result);
	}

	@GetMapping("/{reservationId}")
	public AdminReservationResponse getReservation(@PathVariable Long reservationId) {
		final AdminReservationResult result = service.getReservation(reservationId);
		return AdminReservationResponse.from(result);
	}
}
