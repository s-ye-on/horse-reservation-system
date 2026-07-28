package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationApplicationIdempotencyStatus {
	PROCESSING("processing"),
	COMPLETED("completed");

	private final String databaseValue;

	ReservationApplicationIdempotencyStatus(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static ReservationApplicationIdempotencyStatus fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(status -> status.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
