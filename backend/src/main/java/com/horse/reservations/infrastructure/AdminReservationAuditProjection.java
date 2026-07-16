package com.horse.reservations.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.ReservationStatus;

public interface AdminReservationAuditProjection {

	Long getAuditLogId();

	Long getReservationId();

	Long getMemberId();

	String getMemberName();

	String getActorAuthSubject();

	ReservationActorType getActorType();

	ReservationChangeType getChangeType();

	ReservationStatus getFromStatus();

	ReservationStatus getToStatus();

	LocalDate getFromLessonDate();

	LocalTime getFromStartTime();

	LocalDate getToLessonDate();

	LocalTime getToStartTime();

	CouponAction getCouponAction();

	String getMemo();

	LocalDateTime getOccurredAt();
}
