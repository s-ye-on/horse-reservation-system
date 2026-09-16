package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;

public interface TemplateFutureReservationProjection {

	Long getReservationId();

	LocalDate getLessonDate();

	LocalTime getStartTime();

	LocalTime getEndTime();

	Long getMemberId();

	String getMemberName();

	String getMemberPhone();

	String getRidingClass();

	String getStatus();

}
