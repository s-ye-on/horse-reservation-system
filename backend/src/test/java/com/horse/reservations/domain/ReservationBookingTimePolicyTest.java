package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

class ReservationBookingTimePolicyTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 22);
	private static final LocalTime START_TIME = LocalTime.of(10, 0);
	private static final Instant LESSON_START = Instant.parse("2026-07-22T01:00:00Z");

	@Test
	void 전날_21시_이후라도_수업_시작_전이면_신규_예약을_허용한다() {
		final ReservationActionAvailability availability = ReservationBookingTimePolicy.evaluate(
			LESSON_DATE,
			START_TIME,
			Instant.parse("2026-07-21T13:00:00Z"));

		assertThat(availability).isEqualTo(ReservationActionAvailability.allow());
	}

	@Test
	void 당일_수업_시작_직전이면_신규_예약을_허용한다() {
		final Instant requestedAt = LESSON_START.minusNanos(1);

		assertThatCode(() -> ReservationBookingTimePolicy.ensureCanBook(
			LESSON_DATE,
			START_TIME,
			requestedAt))
			.doesNotThrowAnyException();
	}

	@Test
	void 정확한_수업_시작_시각부터_신규_예약을_차단한다() {
		final ReservationActionAvailability availability = ReservationBookingTimePolicy.evaluate(
			LESSON_DATE,
			START_TIME,
			LESSON_START);

		assertThat(availability).isEqualTo(ReservationActionAvailability.block(
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED.code()));
		assertReservationException(
			() -> ReservationBookingTimePolicy.ensureCanBook(
				LESSON_DATE,
				START_TIME,
				LESSON_START.plusNanos(1)),
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
	}

	@Test
	void 필수_입력이_없으면_도메인_예외가_발생한다() {
		assertReservationException(
			() -> ReservationBookingTimePolicy.evaluate(null, START_TIME, LESSON_START),
			ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		assertReservationException(
			() -> ReservationBookingTimePolicy.evaluate(LESSON_DATE, null, LESSON_START),
			ExceptionCode.RESERVATION_INVALID_START_TIME);
		assertReservationException(
			() -> ReservationBookingTimePolicy.evaluate(LESSON_DATE, START_TIME, null),
			ExceptionCode.RESERVATION_INVALID_BOOKING_REQUESTED_AT);
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
