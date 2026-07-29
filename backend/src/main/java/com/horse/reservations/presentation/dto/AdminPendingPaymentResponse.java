package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.AdminPendingPaymentResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPendingPaymentResponse(
	Long reservationId,
	Long memberId,
	String memberName,
	String memberPhone,
	String classType,
	LocalDate lessonDate,
	@Schema(type = "string", format = "time") LocalTime startTime,
	String status,
	OffsetDateTime approvalRequestedAt,
	OffsetDateTime paymentDueAt,
	boolean deadlineExceeded
) {

	public static AdminPendingPaymentResponse from(AdminPendingPaymentResult result) {
		return new AdminPendingPaymentResponse(
			result.reservationId(),
			result.memberId(),
			result.memberName(),
			result.memberPhone(),
			result.classType(),
			result.lessonDate(),
			result.startTime(),
			result.status(),
			ApiDateTime.toSeoulOffset(result.approvalRequestedAt()),
			ApiDateTime.toSeoulOffset(result.paymentDueAt()),
			result.deadlineExceeded());
	}
}
