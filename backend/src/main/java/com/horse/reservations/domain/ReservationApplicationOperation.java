package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationApplicationOperation {
	MEMBER_RESERVATION_CREATE("member_reservation_create");

	private final String databaseValue;

	ReservationApplicationOperation(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static ReservationApplicationOperation fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(operation -> operation.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
