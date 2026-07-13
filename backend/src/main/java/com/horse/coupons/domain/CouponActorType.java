package com.horse.coupons.domain;

import java.util.Arrays;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

public enum CouponActorType {

	SYSTEM("system"),
	MEMBER("member"),
	ADMIN("admin");

	private final String value;

	CouponActorType(String value) {
		this.value = value;
	}

	public static CouponActorType fromDatabaseValue(String value) {
		return Arrays.stream(values())
			.filter(actorType -> actorType.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_INVALID_PERSISTED_VALUE));
	}

	public String value() {
		return value;
	}
}
