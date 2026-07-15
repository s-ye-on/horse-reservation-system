package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum CancellationResponsibility {
	MEMBER("member"),
	STABLE("stable"),
	EXCEPTION("exception");

	private final String databaseValue;

	CancellationResponsibility(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static CancellationResponsibility fromRequestValue(String requestValue) {
		if (requestValue == null || requestValue.isBlank()) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_INVALID_CANCELLATION_RESPONSIBILITY);
		}
		return Arrays.stream(values())
			.filter(responsibility -> responsibility.databaseValue.equalsIgnoreCase(requestValue.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_CANCELLATION_RESPONSIBILITY));
	}

	public static CancellationResponsibility fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(responsibility -> responsibility.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
