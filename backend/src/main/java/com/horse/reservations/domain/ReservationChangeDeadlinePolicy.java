package com.horse.reservations.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationChangeDeadlinePolicy {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalTime CHANGE_CUTOFF_TIME = LocalTime.of(21, 0);
	private static final Set<DayOfWeek> WEEKEND_DAYS = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private ReservationChangeDeadlinePolicy() {
	}

	public static ReservationChangeTiming evaluate(LocalDate lessonDate, Instant requestedAt) {
		if (lessonDate == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
		if (requestedAt == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_REQUESTED_AT);
		}

		final LocalDateTime requestedAtSeoul = LocalDateTime.ofInstant(requestedAt, SEOUL_ZONE);
		final LocalDateTime cutoffAt = LocalDateTime.of(lessonDate.minusDays(1), CHANGE_CUTOFF_TIME);
		if (requestedAtSeoul.isBefore(cutoffAt)) {
			return ReservationChangeTiming.BEFORE_CUTOFF;
		}
		if (WEEKEND_DAYS.contains(lessonDate.getDayOfWeek())) {
			return ReservationChangeTiming.AFTER_CUTOFF_WEEKEND;
		}
		return ReservationChangeTiming.AFTER_CUTOFF_WEEKDAY;
	}
}
