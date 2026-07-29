package com.horse.reservations.application;

import java.time.LocalDate;
import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;

public record AdminReservationQueryCriteria(
	ReservationStatus status,
	LocalDate lessonDateFrom,
	LocalDate lessonDateTo,
	RidingClass ridingClass,
	String keyword,
	AdminReservationSort sort,
	int page,
	int size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public static AdminReservationQueryCriteria create(
		String status,
		LocalDate lessonDateFrom,
		LocalDate lessonDateTo,
		String classType,
		String keyword,
		String sort,
		Integer page,
		Integer size,
		LocalDate today
	) {
		if (today == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_DATE_RANGE);
		}
		final LocalDate effectiveFrom = lessonDateFrom == null && lessonDateTo == null
			? today
			: lessonDateFrom;
		if (effectiveFrom != null && lessonDateTo != null && effectiveFrom.isAfter(lessonDateTo)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_DATE_RANGE);
		}
		final int effectivePage = page == null ? DEFAULT_PAGE : page;
		final int effectiveSize = size == null ? DEFAULT_SIZE : size;
		if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > MAX_SIZE) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_PAGE);
		}
		return new AdminReservationQueryCriteria(
			parseStatus(status),
			effectiveFrom,
			lessonDateTo,
			parseRidingClass(classType),
			normalizeKeyword(keyword),
			AdminReservationSort.from(sort),
			effectivePage,
			effectiveSize);
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

	private static RidingClass parseRidingClass(String classType) {
		if (classType == null || classType.isBlank()) {
			return null;
		}
		return Arrays.stream(RidingClass.values())
			.filter(candidate -> candidate.name().equalsIgnoreCase(classType.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_CLASS_TYPE));
	}

	private static String normalizeKeyword(String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.strip();
	}
}
