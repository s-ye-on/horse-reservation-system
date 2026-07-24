package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ReservationTimeSlotProjection {

	LocalDate getLessonDate();

	LocalTime getStartTime();

	Long getMemberId();

	Long getCouponId();
}
