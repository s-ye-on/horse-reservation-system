package com.horse.coupons.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class CouponException extends BusinessException {

	public CouponException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}
}
