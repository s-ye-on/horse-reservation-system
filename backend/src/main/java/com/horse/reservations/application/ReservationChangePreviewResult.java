package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.ReservationChangeTiming;

public record ReservationChangePreviewResult(
	Long reservationId,
	Long targetTimeSlotId,
	LocalDate targetLessonDate,
	LocalTime targetStartTime,
	ReservationChangeTiming timing,
	CouponAction couponAction,
	boolean freeChangeUsed
) {
}
