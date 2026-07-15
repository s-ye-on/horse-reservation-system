package com.horse.reservations.domain;

import java.util.Arrays;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum CouponAction {
	DEDUCT("deduct"),
	RETURN("return"),
	FREE_CHANGE_USED("free_change_used"),
	NONE("none");

	private final String databaseValue;

	CouponAction(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static CouponAction fromRequestValue(String requestValue) {
		if (requestValue == null || requestValue.isBlank()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		}
		return Arrays.stream(values())
			.filter(action -> action.databaseValue.equalsIgnoreCase(requestValue.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ACTION));
	}

	public static CouponAction fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(action -> action.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
