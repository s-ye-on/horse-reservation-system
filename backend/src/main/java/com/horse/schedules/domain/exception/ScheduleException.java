package com.horse.schedules.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class ScheduleException extends BusinessException {

	public ScheduleException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}
}
