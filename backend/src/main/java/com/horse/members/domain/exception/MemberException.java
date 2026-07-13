package com.horse.members.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class MemberException extends BusinessException {

	public MemberException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}

}
