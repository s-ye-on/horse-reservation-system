package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.AdminReservationCancellationService;
import com.horse.reservations.presentation.dto.AdminReservationCancelRequest;
import com.horse.reservations.presentation.dto.ReservationCancellationPreviewResponse;
import com.horse.reservations.presentation.dto.ReservationCancelResponse;

@RestController
@RequestMapping("/api/admin/reservations/{reservationId}")
public class AdminReservationCancelController {

	private final AdminReservationCancellationService service;

	public AdminReservationCancelController(AdminReservationCancellationService service) {
		this.service = service;
	}

	@GetMapping("/cancellation-preview")
	public ReservationCancellationPreviewResponse preview(
		@PathVariable Long reservationId,
		@RequestParam String responsibility
	) {
		return ReservationCancellationPreviewResponse.from(
			service.preview(reservationId, responsibility));
	}

	@PostMapping("/cancel")
	public ReservationCancelResponse cancel(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@RequestBody AdminReservationCancelRequest request
	) {
		return ReservationCancelResponse.from(service.cancel(
			reservationId,
			adminSubject,
			request.responsibility(),
			request.couponAction(),
			request.memo()));
	}
}
