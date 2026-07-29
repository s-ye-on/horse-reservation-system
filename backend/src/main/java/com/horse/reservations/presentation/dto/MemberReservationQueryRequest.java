package com.horse.reservations.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MemberReservationQueryRequest(
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
