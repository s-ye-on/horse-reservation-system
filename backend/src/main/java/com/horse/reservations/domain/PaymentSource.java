package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum PaymentSource {
	COUPON("coupon"),
	SINGLE_PAYMENT("single_payment");

	private final String databaseValue;

	PaymentSource(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static PaymentSource fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(source -> source.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
