package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationNoShowRequest(
	@NotBlank
	String couponAction,
	@NotBlank
	@Size(max = 500)
	String memo
) {
}
