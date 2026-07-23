package com.horse.timeslots.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.timeslots.domain.exception.TimeSlotException;

class TimeSlotCapacityTest {

	@Test
	void 시간대를_생성하고_운영_마감과_재개_상태를_변경한다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();

		assertThat(timeSlot.getEndTime()).isEqualTo(LocalTime.of(9, 45));
		assertThat(timeSlot.getSource()).isEqualTo(TimeSlotSource.MANUAL);

		timeSlot.changeClosedStatus(true);

		assertThat(timeSlot.isClosed()).isTrue();

		timeSlot.changeClosedStatus(false);

		assertThat(timeSlot.isClosed()).isFalse();
	}

	@Test
	void 자정에_닿거나_넘어가는_시간대는_생성할_수_없다() {
		assertTimeSlotException(
			() -> TimeSlotCapacity.create(
				LocalDate.of(2026, 8, 1),
				LocalTime.of(23, 15),
				8,
				4,
				validClassCapacities()),
			ExceptionCode.TIMESLOT_INVALID_LESSON_INTERVAL);
	}

	@Test
	void 전체와_원형_정원_제한을_검증한다() {
		assertTimeSlotException(
			() -> TimeSlotCapacity.create(
				LocalDate.of(2026, 8, 1),
				LocalTime.of(9, 0),
				9,
				4,
				validClassCapacities()),
			ExceptionCode.TIMESLOT_INVALID_TOTAL_CAPACITY);
		assertTimeSlotException(
			() -> TimeSlotCapacity.create(
				LocalDate.of(2026, 8, 1),
				LocalTime.of(9, 0),
				3,
				4,
				validClassCapacities()),
			ExceptionCode.TIMESLOT_INVALID_ROUND_ARENA_CAPACITY);
	}

	@Test
	void 모든_클래스의_정원이_유효해야_한다() {
		final Map<String, Integer> missingClass = new java.util.HashMap<>(validClassCapacities());
		missingClass.remove("JUMPING");

		assertTimeSlotException(
			() -> TimeSlotCapacity.create(
				LocalDate.of(2026, 8, 1),
				LocalTime.of(9, 0),
				8,
				4,
				missingClass),
			ExceptionCode.TIMESLOT_INVALID_CLASS_CAPACITIES);
	}

	@Test
	void 마감_상태가_없으면_변경할_수_없다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();

		assertTimeSlotException(
			() -> timeSlot.changeClosedStatus(null),
			ExceptionCode.TIMESLOT_INVALID_CLOSED_STATUS);
	}

	private TimeSlotCapacity createTimeSlot() {
		return TimeSlotCapacity.create(
			LocalDate.of(2026, 8, 1),
			LocalTime.of(9, 0),
			8,
			4,
			validClassCapacities());
	}

	private Map<String, Integer> validClassCapacities() {
		return Map.of(
			"FIRST_RIDE", 2,
			"ROUND_BEGINNER", 2,
			"ROUND_TROT", 2,
			"LARGE_ARENA_BEGINNER", 3,
			"LARGE_ARENA_TROT", 3,
			"DRESSAGE", 1,
			"JUMPING", 1);
	}

	private void assertTimeSlotException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(TimeSlotException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}

}
