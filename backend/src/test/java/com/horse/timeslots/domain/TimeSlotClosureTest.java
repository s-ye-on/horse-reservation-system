package com.horse.timeslots.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;

class TimeSlotClosureTest {

	private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);

	@Test
	void 휴강_작업을_시작하고_완료한다() {
		final TimeSlotClosure closure = TimeSlotClosure.start(
			1L,
			"우천",
			"admin",
			STARTED_AT);

		assertThat(closure.getStatus()).isEqualTo(TimeSlotClosureStatus.IN_PROGRESS);
		assertThat(closure.complete("admin", STARTED_AT.plusMinutes(5))).isTrue();
		assertThat(closure.getStatus()).isEqualTo(TimeSlotClosureStatus.COMPLETED);
	}

	@Test
	void 완료된_휴강은_철회할_수_없다() {
		final TimeSlotClosure closure = TimeSlotClosure.start(
			1L,
			"우천",
			"admin",
			STARTED_AT);
		closure.complete("admin", STARTED_AT.plusMinutes(5));

		assertThatThrownBy(() -> closure.withdraw("admin", STARTED_AT.plusMinutes(10)))
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.TIMESLOT_CLOSURE_NOT_IN_PROGRESS.code()));
	}
}
