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

		timeSlot.changeAdminClosed(true);

		assertThat(timeSlot.isClosed()).isTrue();

		timeSlot.changeAdminClosed(false);

		assertThat(timeSlot.isClosed()).isFalse();
	}

	@Test
	void 관리자_휴강은_관리자_원인만_변경한다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();

		timeSlot.changeAdminClosed(true);

		assertThat(timeSlot.isAdminClosed()).isTrue();
		assertThat(timeSlot.isRecurringHolidayClosed()).isFalse();
		assertThat(timeSlot.isTemplateInactiveClosed()).isFalse();
		assertThat(timeSlot.isClosed()).isTrue();
	}

	@Test
	void 관리자_휴강을_해제해도_정기_휴일_원인이_남으면_마감은_유지된다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();
		timeSlot.changeAdminClosed(true);
		timeSlot.changeRecurringHolidayClosed(true);

		timeSlot.changeAdminClosed(false);

		assertThat(timeSlot.isAdminClosed()).isFalse();
		assertThat(timeSlot.isRecurringHolidayClosed()).isTrue();
		assertThat(timeSlot.isClosed()).isTrue();
	}

	@Test
	void 정기_휴일_원인을_적용하고_해제한다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();

		timeSlot.changeRecurringHolidayClosed(true);
		assertThat(timeSlot.isClosed()).isTrue();

		timeSlot.changeRecurringHolidayClosed(false);
		assertThat(timeSlot.isClosed()).isFalse();
	}

	@Test
	void 비활성_템플릿_원인을_적용하고_해제한다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();

		timeSlot.changeTemplateInactiveClosed(true);
		assertThat(timeSlot.isClosed()).isTrue();

		timeSlot.changeTemplateInactiveClosed(false);
		assertThat(timeSlot.isClosed()).isFalse();
	}

	@Test
	void 여러_마감_원인_중_하나만_해제하면_마감은_유지된다() {
		final TimeSlotCapacity timeSlot = createTimeSlot();
		timeSlot.changeAdminClosed(true);
		timeSlot.changeRecurringHolidayClosed(true);
		timeSlot.changeTemplateInactiveClosed(true);

		timeSlot.changeRecurringHolidayClosed(false);

		assertThat(timeSlot.isAdminClosed()).isTrue();
		assertThat(timeSlot.isRecurringHolidayClosed()).isFalse();
		assertThat(timeSlot.isTemplateInactiveClosed()).isTrue();
		assertThat(timeSlot.isClosed()).isTrue();
	}

	@Test
	void 템플릿_occurrence는_자동_마감_원인만_동기화한다() {
		final TimeSlotCapacity timeSlot = TimeSlotCapacity.createFromTemplate(
			LocalDate.of(2026, 8, 1),
			1L,
			LocalTime.of(9, 0),
			LocalTime.of(9, 45),
			5,
			2,
			validClassCapacities(),
			true);
		timeSlot.changeAdminClosed(true);

		timeSlot.synchronizeTemplateOccurrence(2L, false, true);

		assertThat(timeSlot.getSource()).isEqualTo(TimeSlotSource.TEMPLATE);
		assertThat(timeSlot.getTemplateId()).isEqualTo(2L);
		assertThat(timeSlot.isAdminClosed()).isTrue();
		assertThat(timeSlot.isRecurringHolidayClosed()).isFalse();
		assertThat(timeSlot.isTemplateInactiveClosed()).isTrue();
		assertThat(timeSlot.getTotalCapacity()).isEqualTo(5);
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
			() -> timeSlot.changeAdminClosed(null),
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
			"CANTER_BEGINNER", 3,
			"CANTER", 3,
			"DRESSAGE", 1,
			"JUMPING", 1);
	}

	private void assertTimeSlotException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(TimeSlotException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}

}
