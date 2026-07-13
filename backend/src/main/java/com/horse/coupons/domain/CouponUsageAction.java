package com.horse.coupons.domain;

import java.util.Arrays;

import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;

public enum CouponUsageAction {

	HELD("held"),
	CONFIRMED("confirmed"),
	USED("used"),
	RELEASED("released"),
	DEDUCTED("deducted"),
	EXPIRED("expired"),
	FREE_CHANGE_USED("free_change_used");

	private final String value;

	CouponUsageAction(String value) {
		this.value = value;
	}

	public static CouponUsageAction fromDatabaseValue(String value) {
		return Arrays.stream(values())
			.filter(action -> action.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_INVALID_PERSISTED_VALUE));
	}

	public String value() {
		return value;
	}
}
