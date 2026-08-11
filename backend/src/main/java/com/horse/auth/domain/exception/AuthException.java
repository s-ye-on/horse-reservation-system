package com.horse.auth.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public class AuthException extends BusinessException {

	public AuthException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}
}
