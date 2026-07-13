package com.horse.coupons.domain;

import java.util.Arrays;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

public enum CouponType {

	GENERAL("general"),
	DRESSAGE("dressage"),
	JUMPING("jumping");

	private final String value;

	CouponType(String value) {
		this.value = value;
	}

	public static CouponType fromRequestValue(String value) {
		return Arrays.stream(values())
			.filter(type -> type.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_INVALID_TYPE));
	}

	public static CouponType fromDatabaseValue(String value) {
		return Arrays.stream(values())
			.filter(type -> type.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_INVALID_PERSISTED_VALUE));
	}

	public String value() {
		return value;
	}
}
