package com.horse.schedules.presentation.dto;

import com.horse.schedules.application.ScheduleImpactReservationView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"reservationId", "memberId", "memberName", "memberPhone",
	"classType", "status", "paymentSource"
})
public record ScheduleImpactReservationResponse(
	long reservationId,
	long memberId,
	String memberName,
	String memberPhone,
	String classType,
	String status,
	String paymentSource,
	@Schema(nullable = true) Long couponId
) {

	public static ScheduleImpactReservationResponse from(
		ScheduleImpactReservationView view
	) {
		return new ScheduleImpactReservationResponse(
			view.reservationId(),
			view.memberId(),
			view.memberName(),
			view.memberPhone(),
			view.classType(),
			view.status(),
			view.paymentSource(),
			view.couponId());
	}
}
