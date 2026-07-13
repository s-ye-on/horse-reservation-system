package com.horse.reservations.domain.exception;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

public class ReservationException extends BusinessException {

	public ReservationException(ExceptionCode exceptionCode) {
		super(exceptionCode);
	}

}
