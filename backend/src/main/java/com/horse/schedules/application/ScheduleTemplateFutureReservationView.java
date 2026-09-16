package com.horse.schedules.application;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.TemplateFutureReservationProjection;

public record ScheduleTemplateFutureReservationView(
	long reservationId,
	LocalDate lessonDate,
	LocalTime startTime,
	LocalTime endTime,
	long memberId,
	String memberName,
	String memberPhone,
	RidingClass ridingClass,
	ReservationStatus status
) {

	public static ScheduleTemplateFutureReservationView from(
		TemplateFutureReservationProjection projection
	) {
		return new ScheduleTemplateFutureReservationView(
			projection.getReservationId(),
			projection.getLessonDate(),
			projection.getStartTime(),
			projection.getEndTime(),
			projection.getMemberId(),
			projection.getMemberName(),
			projection.getMemberPhone(),
			RidingClass.valueOf(projection.getRidingClass()),
			ReservationStatus.fromDatabaseValue(projection.getStatus()));
	}
}
