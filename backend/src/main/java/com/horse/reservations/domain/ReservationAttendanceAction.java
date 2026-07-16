package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationAttendanceAction {
	COMPLETE("complete"),
	NO_SHOW("no_show");

	private final String requestValue;

	ReservationAttendanceAction(String requestValue) {
		this.requestValue = requestValue;
	}

	public String requestValue() {
		return requestValue;
	}

	public static ReservationAttendanceAction fromRequestValue(String requestValue) {
		if (requestValue == null || requestValue.isBlank()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_ATTENDANCE_ACTION);
		}
		return Arrays.stream(values())
			.filter(action -> action.requestValue.equalsIgnoreCase(requestValue.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_ATTENDANCE_ACTION));
	}
}
