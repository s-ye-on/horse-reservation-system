package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.exception.ReservationException;

public record AdminReservationAuditQueryCriteria(
	String keyword,
	Long reservationId,
	LocalDateTime occurredAtFrom,
	LocalDateTime occurredAtTo,
	ReservationActorType actorType,
	ReservationChangeType changeType,
	int page,
	int size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public static AdminReservationAuditQueryCriteria create(
		String keyword,
		Long reservationId,
		LocalDate occurredDateFrom,
		LocalDate occurredDateTo,
		String actorType,
		String changeType,
		Integer page,
		Integer size
	) {
		if (occurredDateFrom != null && occurredDateTo != null
			&& occurredDateFrom.isAfter(occurredDateTo)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_AUDIT_DATE_RANGE);
		}
		final int effectivePage = page == null ? DEFAULT_PAGE : page;
		final int effectiveSize = size == null ? DEFAULT_SIZE : size;
		if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > MAX_SIZE) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_AUDIT_PAGE);
		}
		return new AdminReservationAuditQueryCriteria(
			normalizeKeyword(keyword),
			reservationId,
			occurredDateFrom == null ? null : occurredDateFrom.atStartOfDay(),
			occurredDateTo == null ? null : occurredDateTo.atTime(LocalTime.MAX),
			parseActorType(actorType),
			parseChangeType(changeType),
			effectivePage,
			effectiveSize);
	}

	private static String normalizeKeyword(String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.strip();
	}

	private static ReservationActorType parseActorType(String actorType) {
		if (actorType == null || actorType.isBlank()) {
			return null;
		}
		return Arrays.stream(ReservationActorType.values())
			.filter(candidate -> candidate.databaseValue().equalsIgnoreCase(actorType.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_AUDIT_ACTOR_TYPE));
	}

	private static ReservationChangeType parseChangeType(String changeType) {
		if (changeType == null || changeType.isBlank()) {
			return null;
		}
		return Arrays.stream(ReservationChangeType.values())
			.filter(candidate -> candidate.databaseValue().equalsIgnoreCase(changeType.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_AUDIT_CHANGE_TYPE));
	}
}
