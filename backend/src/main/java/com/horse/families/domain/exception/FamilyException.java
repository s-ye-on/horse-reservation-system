package com.horse.families.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class FamilyException extends BusinessException {

	public FamilyException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}
}
