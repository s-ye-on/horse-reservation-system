package com.horse.reservations.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationConstraintViolationTranslator {

	private static final String ACTIVE_MEMBER_SLOT_CONSTRAINT = "uk_reservations_active_member_slot";

	private ReservationConstraintViolationTranslator() {
	}

	public static RuntimeException translate(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				final String constraintName = constraintViolation.getConstraintName();
				if (ACTIVE_MEMBER_SLOT_CONSTRAINT.equals(constraintName)
					|| (constraintName != null
						&& constraintName.endsWith("." + ACTIVE_MEMBER_SLOT_CONSTRAINT))) {
					return new ReservationException(
						ExceptionCode.RESERVATION_OVERLAPPING_ACTIVE_RESERVATION);
				}
			}
			cause = cause.getCause();
		}
		return exception;
	}
}
