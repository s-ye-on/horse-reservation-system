package com.horse.timeslots.presentation.dto;

import com.horse.timeslots.application.TimeSlotClosureImpactView;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {
	"reservationId", "reservationStatusAtStart", "currentStatus", "resolved",
	"moved", "memberId", "memberName", "memberPhone", "classType", "paymentSource"
})
public record TimeSlotClosureImpactResponse(
	long reservationId,
	String reservationStatusAtStart,
	String currentStatus,
	boolean resolved,
	boolean moved,
	long memberId,
	String memberName,
	String memberPhone,
	String classType,
	String paymentSource,
	Long couponId
) {

	public static TimeSlotClosureImpactResponse from(TimeSlotClosureImpactView view) {
		return new TimeSlotClosureImpactResponse(
			view.reservationId(),
			view.reservationStatusAtStart(),
			view.currentStatus(),
			view.resolved(),
			view.moved(),
			view.memberId(),
			view.memberName(),
			view.memberPhone(),
			view.classType(),
			view.paymentSource(),
			view.couponId());
	}
}
