package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.members.domain.Member;
import com.horse.reservations.domain.Reservation;

public record AdminPendingPaymentResult(
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

	public static AdminPendingPaymentResult from(
		Reservation reservation,
		Member member,
		LocalDateTime now
	) {
		return new AdminPendingPaymentResult(
			reservation.getId(),
			member.getId(),
			member.getName(),
			member.getPhone(),
			reservation.getRidingClass().name(),
			reservation.getLessonDate(),
			reservation.getStartTime(),
			reservation.getStatus().databaseValue(),
			reservation.getApprovalRequestedAt(),
			reservation.getPaymentDueAt(),
			!now.isBefore(reservation.getPaymentDueAt()));
	}
}
