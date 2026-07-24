package com.horse.reservations.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationBookingTimePolicy {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Duration MEMBER_BOOKING_LEAD_TIME = Duration.ofHours(3);

	private ReservationBookingTimePolicy() {
	}

	public static ReservationActionAvailability evaluate(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		validateInputs(lessonDate, startTime, requestedAt);
		return blockedCode(lessonDate, startTime, requestedAt)
			.map(code -> ReservationActionAvailability.block(code.code()))
			.orElseGet(ReservationActionAvailability::allow);
	}

	public static void ensureCanBook(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		validateInputs(lessonDate, startTime, requestedAt);
		blockedCode(lessonDate, startTime, requestedAt)
			.ifPresent(code -> {
				throw new ReservationException(code);
			});
	}

	private static Optional<ExceptionCode> blockedCode(
		LocalDate lessonDate,
		LocalTime startTime,
		Instant requestedAt
	) {
		final LocalDateTime lessonStartAt = LocalDateTime.of(lessonDate, startTime);
		final LocalDateTime requestedAtSeoul = LocalDateTime.ofInstant(requestedAt, SEOUL_ZONE);
		if (!requestedAtSeoul.isBefore(lessonStartAt)) {
			return Optional.of(ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		}
		final LocalDateTime bookingDeadlineAt = lessonStartAt.minus(MEMBER_BOOKING_LEAD_TIME);
		if (requestedAtSeoul.isAfter(bookingDeadlineAt)) {
			return Optional.of(ExceptionCode.RESERVATION_BOOKING_DEADLINE_PASSED);
		}
		return Optional.empty();
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
