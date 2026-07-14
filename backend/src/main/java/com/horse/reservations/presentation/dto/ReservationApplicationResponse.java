package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.application.ReservationApplicationResult;

public record ReservationApplicationResponse(
	Long reservationId,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	String paymentSource,
	ReservationCouponResponse coupon,
	LocalDateTime paymentDueAt
) {

	public static ReservationApplicationResponse from(ReservationApplicationResult result) {
		return new ReservationApplicationResponse(
			result.reservationId(),
			result.ridingClass().name(),
			result.lessonDate(),
			result.startTime(),
			result.status().databaseValue(),
			result.paymentSource().databaseValue(),
			ReservationCouponResponse.from(result.coupon()),
			result.paymentDueAt());
	}
}
