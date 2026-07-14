package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;

public record ReservationApplicationResult(
	Long reservationId,
	RidingClass ridingClass,
	LocalDate lessonDate,
	LocalTime startTime,
	ReservationStatus status,
	PaymentSource paymentSource,
	ReservationCouponResult coupon,
	LocalDateTime paymentDueAt
) {

	public static ReservationApplicationResult coupon(
		Reservation reservation,
		ReservationCouponResult coupon
	) {
		return new ReservationApplicationResult(
			reservation.getId(),
			reservation.getRidingClass(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus(),
			reservation.getPaymentSource(),
			coupon,
			null);
	}
}
