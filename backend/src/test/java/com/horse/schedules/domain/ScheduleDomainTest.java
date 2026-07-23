package com.horse.schedules.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.exception.ScheduleException;

class ScheduleDomainTest {

	@Test
	void 정규_시간표_템플릿을_생성한다() {
		final RegularScheduleTemplate template = createTemplate();

		assertThat(template.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
		assertThat(template.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(template.getEndTime()).isEqualTo(LocalTime.of(9, 45));
		assertThat(template.getTotalCapacity()).isEqualTo(8);
		assertThat(template.getRoundArenaCapacity()).isEqualTo(4);
		assertThat(template.getClassCapacities()).isEqualTo(validClassCapacities());
		assertThat(template.isActive()).isTrue();
		assertThat(template.getCreatedBy()).isEqualTo("schedule-admin");
		assertThat(template.getUpdatedBy()).isEqualTo("schedule-admin");
	}

	@Test
	void 정확히_45분이_아닌_템플릿은_생성할_수_없다() {
		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				DayOfWeek.TUESDAY,
				LocalTime.of(9, 0),
				LocalTime.of(9, 30),
				8,
				4,
				validClassCapacities(),
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_LESSON_INTERVAL);
	}

	@Test
	void 자정을_넘는_템플릿은_생성할_수_없다() {
		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				DayOfWeek.TUESDAY,
				LocalTime.of(23, 30),
				LocalTime.of(0, 15),
				8,
				4,
				validClassCapacities(),
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_LESSON_INTERVAL);
	}

	@Test
	void 템플릿의_요일과_정원과_관리자를_검증한다() {
		final Map<String, Integer> missingClass = new HashMap<>(validClassCapacities());
		missingClass.remove("JUMPING");

		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				null,
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				8,
				4,
				validClassCapacities(),
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_DAY_OF_WEEK);
		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				DayOfWeek.TUESDAY,
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				9,
				4,
				validClassCapacities(),
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_TOTAL_CAPACITY);
		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				DayOfWeek.TUESDAY,
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				8,
				4,
				missingClass,
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_CLASS_CAPACITIES);
		assertScheduleException(
			() -> RegularScheduleTemplate.create(
				DayOfWeek.TUESDAY,
				LocalTime.of(9, 0),
				LocalTime.of(9, 45),
				8,
				4,
				validClassCapacities(),
				" "),
			ExceptionCode.SCHEDULE_INVALID_ACTOR);
	}

	@Test
	void 정기_휴일_규칙을_생성한다() {
		final RecurringHolidayRule rule = RecurringHolidayRule.create(
			DayOfWeek.MONDAY,
			LocalDate.of(2026, 7, 1),
			null,
			"정기 휴무",
			"schedule-admin");

		assertThat(rule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(rule.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(rule.getEffectiveTo()).isNull();
		assertThat(rule.getReason()).isEqualTo("정기 휴무");
		assertThat(rule.isActive()).isTrue();
	}

	@Test
	void 정기_휴일의_종료일은_시작일보다_빠를_수_없다() {
		assertScheduleException(
			() -> RecurringHolidayRule.create(
				DayOfWeek.MONDAY,
				LocalDate.of(2026, 7, 2),
				LocalDate.of(2026, 7, 1),
				"정기 휴무",
				"schedule-admin"),
			ExceptionCode.SCHEDULE_INVALID_EFFECTIVE_DATE);
	}

	@Test
	void 운영_날짜는_NORMAL_상태로_생성한다() {
		final ScheduleDate scheduleDate = ScheduleDate.create(
			LocalDate.of(2026, 8, 1),
			1L);

		assertThat(scheduleDate.getScheduleDate()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(scheduleDate.getStatus()).isEqualTo(ScheduleDateStatus.NORMAL);
		assertThat(scheduleDate.getAppliedConfigVersion()).isEqualTo(1);
	}

	@Test
	void 운영_날짜의_설정_버전은_양수여야_한다() {
		assertScheduleException(
			() -> ScheduleDate.create(LocalDate.of(2026, 8, 1), 0L),
			ExceptionCode.SCHEDULE_INVALID_CONFIG_VERSION);
	}

	@Test
	void 일정_감사_로그는_입력_상태를_복사하여_보관한다() {
		final Map<String, Object> fromState = new HashMap<>();
		fromState.put("active", true);
		final ScheduleAuditLog auditLog = ScheduleAuditLog.create(
			ScheduleAuditTargetType.TEMPLATE,
			"template:1",
			"CREATED",
			fromState,
			Map.of("active", false),
			"schedule-admin",
			"운영 일정 변경",
			Map.of("version", 2));

		fromState.put("active", false);

		assertThat(auditLog.getFromState()).containsEntry("active", true);
		assertThat(auditLog.getToState()).containsEntry("active", false);
		assertThat(auditLog.getActorAuthSubject()).isEqualTo("schedule-admin");
	}

	private RegularScheduleTemplate createTemplate() {
		return RegularScheduleTemplate.create(
			DayOfWeek.TUESDAY,
			LocalTime.of(9, 0),
			LocalTime.of(9, 45),
			8,
			4,
			validClassCapacities(),
			"schedule-admin");
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

	private void assertScheduleException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ScheduleException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
