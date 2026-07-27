package com.horse.schedules.domain.exception;

import java.util.Map;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class ScheduleException extends BusinessException {

	public ScheduleException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}

	public ScheduleException(ExceptionCode exceptionCode, Map<String, Object> details) {
		super(exceptionCode, details);
	}
}
