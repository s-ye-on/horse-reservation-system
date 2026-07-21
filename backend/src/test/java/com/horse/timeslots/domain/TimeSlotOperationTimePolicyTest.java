package com.horse.timeslots.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.exception.TimeSlotException;

class TimeSlotOperationTimePolicyTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
	private static final LocalTime START_TIME = LocalTime.of(10, 0);

	@Test
	void 수업_시작_직전에는_일반_관리_작업을_허용한다() {
		assertThatCode(() -> TimeSlotOperationTimePolicy.ensureNotStarted(
			LESSON_DATE,
			START_TIME,
			LocalDateTime.of(LESSON_DATE, START_TIME).minusNanos(1)))
			.doesNotThrowAnyException();
	}

	@Test
	void 수업_시작_정각부터_일반_관리_작업을_거부한다() {
		assertThatThrownBy(() -> TimeSlotOperationTimePolicy.ensureNotStarted(
			LESSON_DATE,
			START_TIME,
			LocalDateTime.of(LESSON_DATE, START_TIME)))
			.isInstanceOfSatisfying(TimeSlotException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
					.isEqualTo(ExceptionCode.TIMESLOT_LESSON_ALREADY_STARTED.code()));
	}

}
