package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.AdminReservationAuditProjection;

public record AdminReservationAuditResult(
	Long auditLogId,
	Long reservationId,
	Long memberId,
	String memberName,
	String actorAuthSubject,
	ReservationActorType actorType,
	ReservationChangeType changeType,
	ReservationStatus fromStatus,
	ReservationStatus toStatus,
	LocalDate fromLessonDate,
	LocalTime fromStartTime,
	LocalDate toLessonDate,
	LocalTime toStartTime,
	CouponAction couponAction,
	Long couponId,
	LocalDateTime paymentDueAt,
	String memo,
	LocalDateTime occurredAt
) {

	public static AdminReservationAuditResult from(AdminReservationAuditProjection projection) {
		return new AdminReservationAuditResult(
			projection.getAuditLogId(),
			projection.getReservationId(),
			projection.getMemberId(),
			projection.getMemberName(),
			projection.getActorAuthSubject(),
			projection.getActorType(),
			projection.getChangeType(),
			projection.getFromStatus(),
			projection.getToStatus(),
			projection.getFromLessonDate(),
			projection.getFromStartTime(),
			projection.getToLessonDate(),
			projection.getToStartTime(),
			projection.getCouponAction(),
			projection.getCouponId(),
			projection.getPaymentDueAt(),
			projection.getMemo(),
			projection.getOccurredAt());
	}
}
