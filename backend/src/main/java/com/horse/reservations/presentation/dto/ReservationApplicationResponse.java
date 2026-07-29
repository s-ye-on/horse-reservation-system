package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.ReservationApplicationResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReservationApplicationResponse(
	Long reservationId,
	String classType,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	String status,
	String paymentSource,
	@Schema(nullable = true) ReservationCouponResponse coupon,
	@Schema(nullable = true) OffsetDateTime paymentDueAt
) {

	public static ReservationApplicationResponse from(ReservationApplicationResult result) {
		return new ReservationApplicationResponse(
			result.reservationId(),
			result.ridingClass().name(),
			result.lessonDate(),
			result.startTime(),
			result.status().databaseValue(),
			result.paymentSource().databaseValue(),
			result.coupon() == null ? null : ReservationCouponResponse.from(result.coupon()),
			ApiDateTime.toSeoulOffset(result.paymentDueAt()));
	}
}
