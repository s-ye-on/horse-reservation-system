package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.BulkReservationAttendanceCommand;
import com.horse.reservations.application.BulkReservationAttendanceResult;
import com.horse.reservations.application.BulkReservationAttendanceService;
import com.horse.reservations.presentation.dto.BulkReservationAttendanceRequest;
import com.horse.reservations.presentation.dto.BulkReservationAttendanceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminBulkReservationAttendanceController {

	private final BulkReservationAttendanceService service;

	public AdminBulkReservationAttendanceController(BulkReservationAttendanceService service) {
		this.service = service;
	}

	@PostMapping("/complete-bulk")
	public BulkReservationAttendanceResponse processBulkAttendance(
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody BulkReservationAttendanceRequest request
	) {
		final BulkReservationAttendanceResult result = service.process(
			request.lessonDate(),
			request.startTime(),
			adminSubject,
			request.items().stream()
				.map(item -> new BulkReservationAttendanceCommand(
					item.reservationId(),
					item.action(),
					item.couponAction(),
					item.memo()))
				.toList());
		return BulkReservationAttendanceResponse.from(result);
	}
}
