package com.horse.timeslots.domain.exception;

import java.util.Map;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class TimeSlotException extends BusinessException {

	public TimeSlotException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}

	public TimeSlotException(ExceptionCode exceptionCode, Map<String, Object> details) {
		super(exceptionCode, details);
	}

}
