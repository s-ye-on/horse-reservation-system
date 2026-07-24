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
	private static final Instant BOOKING_DEADLINE = LESSON_START.minusSeconds(3 * 60 * 60);

	@Test
	void 정확히_수업_시작_3시간_전이면_신규_예약을_허용한다() {
		final ReservationActionAvailability availability = ReservationBookingTimePolicy.evaluate(
			LESSON_DATE,
			START_TIME,
			BOOKING_DEADLINE);

		assertThat(availability).isEqualTo(ReservationActionAvailability.allow());
	}

	@Test
	void 예약_마감_직전이면_신규_예약을_허용한다() {
		final Instant requestedAt = BOOKING_DEADLINE.minusNanos(1);

		assertThatCode(() -> ReservationBookingTimePolicy.ensureCanBook(
			LESSON_DATE,
			START_TIME,
			requestedAt))
			.doesNotThrowAnyException();
	}

	@Test
	void 예약_마감을_1나노초_지난_시점부터_신규_예약을_차단한다() {
		final Instant requestedAt = BOOKING_DEADLINE.plusNanos(1);

		final ReservationActionAvailability availability = ReservationBookingTimePolicy.evaluate(
			LESSON_DATE,
			START_TIME,
			requestedAt);

		assertThat(availability).isEqualTo(ReservationActionAvailability.block(
			ExceptionCode.RESERVATION_BOOKING_DEADLINE_PASSED.code()));
		assertReservationException(
			() -> ReservationBookingTimePolicy.ensureCanBook(LESSON_DATE, START_TIME, requestedAt),
			ExceptionCode.RESERVATION_BOOKING_DEADLINE_PASSED);
	}

	@Test
	void 자정에_인접한_수업도_서울_시각으로_예약_마감을_계산한다() {
		final LocalDate lessonDate = LocalDate.of(2026, 7, 23);
		final LocalTime startTime = LocalTime.of(1, 30);
		final Instant deadline = Instant.parse("2026-07-22T13:30:00Z");

		assertThat(ReservationBookingTimePolicy.evaluate(lessonDate, startTime, deadline))
			.isEqualTo(ReservationActionAvailability.allow());
		assertThat(ReservationBookingTimePolicy.evaluate(
			lessonDate,
			startTime,
			deadline.plusNanos(1)))
			.isEqualTo(ReservationActionAvailability.block(
				ExceptionCode.RESERVATION_BOOKING_DEADLINE_PASSED.code()));
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
