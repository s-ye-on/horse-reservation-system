package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.application.AdminReservationAuditResult;

public record AdminReservationAuditResponse(
	Long auditLogId,
	Long reservationId,
	Long memberId,
	String memberName,
	String actorAuthSubject,
	String actorType,
	String changeType,
	String fromStatus,
	String toStatus,
	LocalDate fromLessonDate,
	LocalTime fromStartTime,
	LocalDate toLessonDate,
	LocalTime toStartTime,
	String couponAction,
	String memo,
	LocalDateTime occurredAt
) {

	public static AdminReservationAuditResponse from(AdminReservationAuditResult result) {
		return new AdminReservationAuditResponse(
			result.auditLogId(),
			result.reservationId(),
			result.memberId(),
			result.memberName(),
			result.actorAuthSubject(),
			result.actorType().databaseValue(),
			result.changeType().databaseValue(),
			result.fromStatus().databaseValue(),
			result.toStatus().databaseValue(),
			result.fromLessonDate(),
			result.fromStartTime(),
			result.toLessonDate(),
			result.toStartTime(),
			result.couponAction().databaseValue(),
			result.memo(),
			result.occurredAt());
	}
}
