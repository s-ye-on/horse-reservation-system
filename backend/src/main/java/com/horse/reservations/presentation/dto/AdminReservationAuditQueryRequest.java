package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record AdminReservationAuditQueryRequest(
	String keyword,
	@Positive
	Long reservationId,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateTo,
	String actorType,
	String changeType,
	@Min(0)
	Integer page,
	@Min(1)
	@Max(100)
	Integer size
) {
}
