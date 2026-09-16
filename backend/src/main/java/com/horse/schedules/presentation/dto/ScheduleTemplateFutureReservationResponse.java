package com.horse.schedules.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.horse.schedules.application.ScheduleTemplateFutureReservationView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"reservationId", "lessonDate", "startTime", "endTime", "memberId",
	"memberName", "memberPhone", "ridingClass", "status"
})
public record ScheduleTemplateFutureReservationResponse(
	long reservationId,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	@Schema(type = "string", format = "time") LocalTime endTime,
	long memberId,
	String memberName,
	String memberPhone,
	String ridingClass,
	String status
) {

	public static ScheduleTemplateFutureReservationResponse from(
		ScheduleTemplateFutureReservationView view
	) {
		return new ScheduleTemplateFutureReservationResponse(
			view.reservationId(),
			view.lessonDate(),
			view.startTime(),
			view.endTime(),
			view.memberId(),
			view.memberName(),
			view.memberPhone(),
			view.ridingClass().name(),
			view.status().databaseValue());
	}
}
