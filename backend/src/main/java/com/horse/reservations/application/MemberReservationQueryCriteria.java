package com.horse.reservations.application;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationDisplayGroup;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;

public record MemberReservationQueryCriteria(
	ReservationDisplayGroup displayGroup,
	ReservationStatus status,
	int page,
	int size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public static MemberReservationQueryCriteria create(
		String displayGroup,
		String status,
		Integer page,
		Integer size
	) {
		final int effectivePage = page == null ? DEFAULT_PAGE : page;
		final int effectiveSize = size == null ? DEFAULT_SIZE : size;
		if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > MAX_SIZE) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_PAGE);
		}
		return new MemberReservationQueryCriteria(
			parseDisplayGroup(displayGroup),
			parseStatus(status),
			effectivePage,
			effectiveSize);
	}

	private static ReservationDisplayGroup parseDisplayGroup(String displayGroup) {
		if (displayGroup == null || displayGroup.isBlank()) {
			return null;
		}
		return Arrays.stream(ReservationDisplayGroup.values())
			.filter(candidate -> candidate.name().equalsIgnoreCase(displayGroup.strip()))
			.findFirst()
			.orElseThrow(() ->
				new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_DISPLAY_GROUP));
	}

	private static ReservationStatus parseStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		return Arrays.stream(ReservationStatus.values())
			.filter(candidate -> candidate.databaseValue().equalsIgnoreCase(status.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_STATUS));
	}
}
