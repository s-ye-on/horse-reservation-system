package com.horse.coupons.domain;

import java.util.Arrays;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

public enum CouponStatus {

	ACTIVE("active"),
	EXPIRED("expired"),
	DEPLETED("depleted");

	private final String value;

	CouponStatus(String value) {
		this.value = value;
	}

	public static CouponStatus fromDatabaseValue(String value) {
		return Arrays.stream(values())
			.filter(status -> status.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_INVALID_PERSISTED_VALUE));
	}

	public String value() {
		return value;
	}
}
