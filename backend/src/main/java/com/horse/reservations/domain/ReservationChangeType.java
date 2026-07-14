package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationChangeType {
	PAYMENT_RESTORED("payment_restored");

	private final String databaseValue;

	ReservationChangeType(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static ReservationChangeType fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(changeType -> changeType.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
