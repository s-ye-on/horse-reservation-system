package com.horse.auth.domain.exception;

import com.horse.global.exception.ExceptionCode;

public final class RefreshTokenReuseDetectedException extends AuthException {

	public RefreshTokenReuseDetectedException() {
		super(ExceptionCode.AUTH_INVALID_REFRESH_TOKEN);
	}
}
