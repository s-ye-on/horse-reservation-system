package com.horse.reservations.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import com.horse.reservations.application.AdminReservationAuditPageResult;
import com.horse.reservations.application.AdminReservationAuditQueryService;
import com.horse.reservations.presentation.dto.AdminReservationAuditPageResponse;
import com.horse.reservations.presentation.dto.AdminReservationAuditQueryRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminReservationAuditQueryController {

	private final AdminReservationAuditQueryService service;

	public AdminReservationAuditQueryController(AdminReservationAuditQueryService service) {
		this.service = service;
	}

	@GetMapping
	public AdminReservationAuditPageResponse getAuditLogs(
		@Valid @ParameterObject @ModelAttribute AdminReservationAuditQueryRequest request
	) {
		final AdminReservationAuditPageResult result = service.getAuditLogs(
			request.keyword(),
			request.reservationId(),
			request.occurredDateFrom(),
			request.occurredDateTo(),
			request.actorType(),
			request.changeType(),
			request.page(),
			request.size());
		return AdminReservationAuditPageResponse.from(result);
	}
}
