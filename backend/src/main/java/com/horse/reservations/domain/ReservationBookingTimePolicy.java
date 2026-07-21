package com.horse.reservations.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationBookingTimePolicy {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private ReservationBookingTimePolicy() {
	}

	public static ReservationActionAvailability evaluate(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		validateInputs(lessonDate, startTime, requestedAt);
		final LocalDateTime lessonStartAt = LocalDateTime.of(lessonDate, startTime);
		final LocalDateTime requestedAtSeoul = LocalDateTime.ofInstant(requestedAt, SEOUL_ZONE);
		if (requestedAtSeoul.isBefore(lessonStartAt)) {
			return ReservationActionAvailability.allow();
		}
		return ReservationActionAvailability.block(
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED.code());
	}

	public static void ensureCanBook(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		final ReservationActionAvailability availability = evaluate(
			lessonDate,
			startTime,
			requestedAt);
		if (!availability.allowed()) {
			throw new ReservationException(ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		}
	}

	private static void validateInputs(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		if (lessonDate == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
		if (startTime == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_START_TIME);
		}
		if (requestedAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_BOOKING_REQUESTED_AT);
		}
	}
}
