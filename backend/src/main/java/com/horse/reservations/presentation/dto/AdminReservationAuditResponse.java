package com.horse.reservations.presentation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.horse.global.time.ApiDateTime;
import com.horse.reservations.application.AdminReservationAuditResult;

import io.swagger.v3.oas.annotations.media.Schema;

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
	@Schema(type = "string", format = "time") LocalTime fromStartTime,
	LocalDate toLessonDate,
	@Schema(type = "string", format = "time") LocalTime toStartTime,
	String couponAction,
	@Schema(nullable = true) Long couponId,
	@Schema(nullable = true) OffsetDateTime paymentDueAt,
	@Schema(nullable = true) String memo,
	OffsetDateTime occurredAt
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
			result.couponId(),
			ApiDateTime.toSeoulOffset(result.paymentDueAt()),
			result.memo(),
			ApiDateTime.toSeoulOffset(result.occurredAt()));
	}
}
