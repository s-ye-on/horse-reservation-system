package com.horse.timeslots.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public final class TimeSlotException extends BusinessException {

	public TimeSlotException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}

}
