package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationActorType {
	MEMBER("member"),
	ADMIN("admin"),
	SYSTEM("system");

	private final String databaseValue;

	ReservationActorType(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static ReservationActorType fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(actorType -> actorType.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
