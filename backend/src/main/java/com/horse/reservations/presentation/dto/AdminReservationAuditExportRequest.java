package com.horse.reservations.presentation.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Positive;

public record AdminReservationAuditExportRequest(
	String keyword,
	@Positive
	Long reservationId,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateFrom,
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate occurredDateTo,
	String actorType,
	String changeType
) {
}
