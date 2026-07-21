package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

class PendingPaymentDeadlinePolicyTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);

	@Test
	void 수업_시작이_두_시간보다_늦으면_신청_후_두_시간을_마감으로_정한다() {
		final LocalDateTime requestedAt = LocalDateTime.of(2026, 7, 31, 20, 0);

		final LocalDateTime deadline = PendingPaymentDeadlinePolicy.calculate(
			LESSON_DATE,
			START_TIME,
			requestedAt);

		assertThat(deadline).isEqualTo(LocalDateTime.of(2026, 7, 31, 22, 0));
	}

	@Test
	void 수업_시작이_두_시간보다_빠르면_수업_시작을_마감으로_정한다() {
		final LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 1, 8, 59, 59);

		final LocalDateTime deadline = PendingPaymentDeadlinePolicy.calculate(
			LESSON_DATE,
			START_TIME,
			requestedAt);

		assertThat(deadline).isEqualTo(LocalDateTime.of(LESSON_DATE, START_TIME));
	}

	@Test
	void 필수_입력이_없으면_도메인_예외가_발생한다() {
		final LocalDateTime requestedAt = LocalDateTime.of(2026, 7, 31, 20, 0);

		assertReservationException(
			() -> PendingPaymentDeadlinePolicy.calculate(null, START_TIME, requestedAt),
			ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		assertReservationException(
			() -> PendingPaymentDeadlinePolicy.calculate(LESSON_DATE, null, requestedAt),
			ExceptionCode.RESERVATION_INVALID_START_TIME);
		assertReservationException(
			() -> PendingPaymentDeadlinePolicy.calculate(LESSON_DATE, START_TIME, null),
			ExceptionCode.RESERVATION_INVALID_APPROVAL_REQUESTED_AT);
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
