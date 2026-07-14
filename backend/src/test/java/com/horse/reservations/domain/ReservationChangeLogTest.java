package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

class ReservationChangeLogTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);

	@Test
	void 만료_예약_복구_행위를_명시적인_전이값으로_생성한다() {
		final ReservationChangeLog changeLog = ReservationChangeLog.paymentRestored(
			1L,
			"restore-admin",
			LESSON_DATE,
			START_TIME,
			"입금 확인 후 복구");

		assertThat(changeLog.getReservationId()).isEqualTo(1L);
		assertThat(changeLog.getActorAuthSubject()).isEqualTo("restore-admin");
		assertThat(changeLog.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(changeLog.getFromStatus()).isEqualTo(ReservationStatus.PAYMENT_EXPIRED);
		assertThat(changeLog.getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(changeLog.getChangeType()).isEqualTo(ReservationChangeType.PAYMENT_RESTORED);
		assertThat(changeLog.getCouponAction()).isEqualTo(CouponAction.NONE);
		assertThat(changeLog.getFromLessonDate()).isEqualTo(LESSON_DATE);
		assertThat(changeLog.getToLessonDate()).isEqualTo(LESSON_DATE);
		assertThat(changeLog.getMemo()).isEqualTo("입금 확인 후 복구");
	}

	@Test
	void 예약_참조와_관리자_인증_주체를_검증한다() {
		assertReservationException(
			() -> ReservationChangeLog.paymentRestored(
				null, "restore-admin", LESSON_DATE, START_TIME, "복구"),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_REFERENCE);
		assertReservationException(
			() -> ReservationChangeLog.paymentRestored(
				1L, " ", LESSON_DATE, START_TIME, "복구"),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_ACTOR);
		assertReservationException(
			() -> ReservationChangeLog.paymentRestored(
				1L, "a".repeat(192), LESSON_DATE, START_TIME, "복구"),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_ACTOR);
	}

	@Test
	void 복구_메모는_필수이며_오백자까지_허용한다() {
		assertThat(ReservationChangeLog.paymentRestored(
			1L, "restore-admin", LESSON_DATE, START_TIME, "가".repeat(500)).getMemo())
			.hasSize(500);
		assertReservationException(
			() -> ReservationChangeLog.paymentRestored(
				1L, "restore-admin", LESSON_DATE, START_TIME, " "),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_MEMO);
		assertReservationException(
			() -> ReservationChangeLog.paymentRestored(
				1L, "restore-admin", LESSON_DATE, START_TIME, "가".repeat(501)),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_MEMO);
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
