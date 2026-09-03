package com.horse.reservations.presentation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import com.horse.reservations.application.AdminMonthlyRideStatisticsResult;
import com.horse.reservations.application.AdminMonthlyRideStatisticsService;
import com.horse.reservations.application.AdminReservationPageResult;
import com.horse.reservations.application.AdminReservationQueryService;
import com.horse.reservations.application.AdminReservationResult;
import com.horse.reservations.application.AdminReservationSummaryResult;
import com.horse.reservations.application.AdminReservationSummaryService;
import com.horse.reservations.application.AdminWeeklyOperationsCalendarResult;
import com.horse.reservations.application.AdminWeeklyOperationsCalendarService;
import com.horse.reservations.presentation.dto.AdminMonthlyRideStatisticsRequest;
import com.horse.reservations.presentation.dto.AdminMonthlyRideStatisticsResponse;
import com.horse.reservations.presentation.dto.AdminReservationPageResponse;
import com.horse.reservations.presentation.dto.AdminReservationQueryRequest;
import com.horse.reservations.presentation.dto.AdminReservationResponse;
import com.horse.reservations.presentation.dto.AdminReservationSummaryRequest;
import com.horse.reservations.presentation.dto.AdminReservationSummaryResponse;
import com.horse.reservations.presentation.dto.AdminWeeklyOperationsCalendarRequest;
import com.horse.reservations.presentation.dto.AdminWeeklyOperationsCalendarResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationQueryController {

	private final AdminReservationQueryService service;
	private final AdminReservationSummaryService summaryService;
	private final AdminMonthlyRideStatisticsService monthlyRideStatisticsService;
	private final AdminWeeklyOperationsCalendarService weeklyOperationsCalendarService;

	public AdminReservationQueryController(
		AdminReservationQueryService service,
		AdminReservationSummaryService summaryService,
		AdminMonthlyRideStatisticsService monthlyRideStatisticsService,
		AdminWeeklyOperationsCalendarService weeklyOperationsCalendarService
	) {
		this.service = service;
		this.summaryService = summaryService;
		this.monthlyRideStatisticsService = monthlyRideStatisticsService;
		this.weeklyOperationsCalendarService = weeklyOperationsCalendarService;
	}

	@GetMapping("/weekly-operations-calendar")
	@Operation(operationId = "getWeeklyOperationsCalendar")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = AdminWeeklyOperationsCalendarResponse.class)))
	public AdminWeeklyOperationsCalendarResponse getWeeklyOperationsCalendar(
		@ParameterObject @ModelAttribute AdminWeeklyOperationsCalendarRequest request
	) {
		final AdminWeeklyOperationsCalendarResult result = weeklyOperationsCalendarService.getCalendar(
			request.referenceDate());
		return AdminWeeklyOperationsCalendarResponse.from(result);
	}

	@GetMapping("/monthly-ride-statistics")
	@Operation(operationId = "getMonthlyRideStatistics")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = AdminMonthlyRideStatisticsResponse.class)))
	public AdminMonthlyRideStatisticsResponse getMonthlyRideStatistics(
		@ParameterObject @ModelAttribute AdminMonthlyRideStatisticsRequest request
	) {
		final AdminMonthlyRideStatisticsResult result = monthlyRideStatisticsService.getStatistics(
			request.month(),
			request.rideType());
		return AdminMonthlyRideStatisticsResponse.from(result);
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
