package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.application.AdminPendingPaymentResult;

public record AdminPendingPaymentResponse(
	Long reservationId,
	Long memberId,
	String memberName,
	String memberPhone,
	String classType,
	LocalDate lessonDate,
	LocalTime startTime,
	String status,
	LocalDateTime approvalRequestedAt,
	LocalDateTime paymentDueAt,
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
			result.approvalRequestedAt(),
			result.paymentDueAt(),
			result.deadlineExceeded());
	}
}
