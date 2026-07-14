package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationRejectRequest(
	@NotBlank
	@Size(max = 500)
	String reason
) {
}
