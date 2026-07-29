package com.horse.reservations.application;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum AdminReservationSort {

	LESSON_START_AT_ASC("lessonStartAt,asc"),
	CREATED_AT_DESC("createdAt,desc");

	private final String queryValue;

	AdminReservationSort(String queryValue) {
		this.queryValue = queryValue;
	}

	public static AdminReservationSort from(String value) {
		if (value == null || value.isBlank()) {
			return LESSON_START_AT_ASC;
		}
		for (AdminReservationSort sort : values()) {
			if (sort.queryValue.equalsIgnoreCase(value.strip())) {
				return sort;
			}
		}
		throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_SORT);
	}
}
