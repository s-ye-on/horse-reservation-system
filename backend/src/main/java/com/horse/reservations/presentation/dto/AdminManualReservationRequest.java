package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminManualReservationRequest(
	@NotNull @Positive Long memberId,
	@NotNull @Positive Long timeSlotId,
	@NotBlank String classType,
	@NotBlank @Size(max = 500) String reason
) {
}
