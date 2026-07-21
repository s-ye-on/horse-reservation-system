package com.horse.reservations.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class PendingPaymentDeadlinePolicy {

	private static final Duration PAYMENT_WAIT_DURATION = Duration.ofHours(2);

	private PendingPaymentDeadlinePolicy() {
	}

	public static LocalDateTime calculate(
		LocalDate lessonDate,
		LocalTime startTime,
		LocalDateTime requestedAt
	) {
		validateInputs(lessonDate, startTime, requestedAt);
		final LocalDateTime lessonStartAt = LocalDateTime.of(lessonDate, startTime);
		final LocalDateTime durationDeadline = requestedAt.plus(PAYMENT_WAIT_DURATION);
		return durationDeadline.isBefore(lessonStartAt) ? durationDeadline : lessonStartAt;
	}

	private static void validateInputs(
		LocalDate lessonDate,
		LocalTime startTime,
		LocalDateTime requestedAt
	) {
		if (lessonDate == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
		if (startTime == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_START_TIME);
		}
		if (requestedAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_APPROVAL_REQUESTED_AT);
		}
	}
}
