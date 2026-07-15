package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

class ReservationChangeDeadlinePolicyTest {

	@Test
	void 서울_기준_수업_전날_21시_직전은_마감_전이다() {
		final ReservationChangeTiming timing = ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 17),
			Instant.parse("2026-07-16T11:59:59Z"));

		assertThat(timing).isEqualTo(ReservationChangeTiming.BEFORE_CUTOFF);
	}

	@Test
	void 서울_기준_수업_전날_21시부터는_마감_후다() {
		final ReservationChangeTiming timing = ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 17),
			Instant.parse("2026-07-16T12:00:00Z"));

		assertThat(timing).isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKDAY);
	}

	@Test
	void 마감_후_토요일과_일요일은_주말로_판정한다() {
		assertThat(ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 18),
			Instant.parse("2026-07-17T12:00:00Z")))
			.isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKEND);
		assertThat(ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 19),
			Instant.parse("2026-07-18T12:00:00Z")))
			.isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKEND);
	}

	@Test
	void 마감_후_월요일부터_금요일은_평일로_판정한다() {
		assertThat(ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 20),
			Instant.parse("2026-07-19T12:00:00Z")))
			.isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKDAY);
		assertThat(ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 24),
			Instant.parse("2026-07-23T12:00:00Z")))
			.isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKDAY);
	}

	@Test
	void 필수_입력이_없으면_도메인_예외가_발생한다() {
		assertThatThrownBy(() -> ReservationChangeDeadlinePolicy.evaluate(
			null,
			Instant.parse("2026-07-16T12:00:00Z")))
			.isInstanceOf(ReservationException.class)
			.extracting(exception -> ((ReservationException)exception).code())
			.isEqualTo(ExceptionCode.RESERVATION_INVALID_LESSON_DATE.code());
		assertThatThrownBy(() -> ReservationChangeDeadlinePolicy.evaluate(
			LocalDate.of(2026, 7, 17),
			null))
			.isInstanceOf(ReservationException.class)
			.extracting(exception -> ((ReservationException)exception).code())
			.isEqualTo(ExceptionCode.RESERVATION_INVALID_CHANGE_REQUESTED_AT.code());
	}
}
