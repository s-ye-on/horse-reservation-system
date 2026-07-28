package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
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
	void 관리자_수동_예약_생성은_최종_상태와_사유를_감사한다() {
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			LocalDateTime.of(LESSON_DATE, START_TIME).minusHours(1),
			LocalDateTime.of(LESSON_DATE, START_TIME).minusHours(2));
		ReflectionTestUtils.setField(reservation, "id", 1L);

		final ReservationChangeLog changeLog = ReservationChangeLog.adminReservationCreated(
			reservation,
			"manual-admin",
			"전화 접수");

		assertThat(changeLog.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(changeLog.getFromStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(changeLog.getToStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(changeLog.getChangeType())
			.isEqualTo(ReservationChangeType.ADMIN_RESERVATION_CREATED);
		assertThat(changeLog.getCouponAction()).isEqualTo(CouponAction.NONE);
		assertThat(changeLog.getMemo()).isEqualTo("전화 접수");
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

	@Test
	void 노쇼_처리_행위를_관리자와_쿠폰_결과로_생성한다() {
		final ReservationChangeLog changeLog = ReservationChangeLog.noShowProcessed(
			1L,
			"no-show-admin",
			LESSON_DATE,
			START_TIME,
			CouponAction.RETURN,
			"질병 예외 반환");

		assertThat(changeLog.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(changeLog.getFromStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(changeLog.getToStatus()).isEqualTo(ReservationStatus.NO_SHOW);
		assertThat(changeLog.getChangeType()).isEqualTo(ReservationChangeType.NO_SHOW_PROCESSED);
		assertThat(changeLog.getCouponAction()).isEqualTo(CouponAction.RETURN);
		assertThat(changeLog.getMemo()).isEqualTo("질병 예외 반환");
	}

	@Test
	void 회원_예약_변경_이력은_상태를_유지하고_사유는_선택값이다() {
		final ReservationChangeLog changeLog = ReservationChangeLog.reservationChanged(
			1L,
			"member-subject",
			ReservationActorType.MEMBER,
			ReservationStatus.CONFIRMED,
			LESSON_DATE,
			START_TIME,
			LESSON_DATE.plusDays(1),
			START_TIME.plusHours(1),
			null);

		assertThat(changeLog.getActorType()).isEqualTo(ReservationActorType.MEMBER);
		assertThat(changeLog.getFromStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(changeLog.getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(changeLog.getChangeType()).isEqualTo(ReservationChangeType.SCHEDULE_CHANGED);
		assertThat(changeLog.getCouponAction()).isEqualTo(CouponAction.NONE);
		assertThat(changeLog.getMemo()).isNull();
	}

	@Test
	void 관리자_예약_변경_이력은_메모가_필수다() {
		final ReservationChangeLog changeLog = ReservationChangeLog.reservationChanged(
			1L,
			"admin-subject",
			ReservationActorType.ADMIN,
			ReservationStatus.PENDING_PAYMENT,
			LESSON_DATE,
			START_TIME,
			LESSON_DATE.plusDays(1),
			START_TIME,
			"관리자 일정 조정");

		assertThat(changeLog.getActorType()).isEqualTo(ReservationActorType.ADMIN);
		assertThat(changeLog.getFromStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(changeLog.getToStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(changeLog.getMemo()).isEqualTo("관리자 일정 조정");
	}

	@Test
	void 마감_후_무료_변경_이력은_쿠폰_행위를_기록한다() {
		final ReservationChangeLog changeLog = ReservationChangeLog.reservationChanged(
			1L,
			"member-subject",
			ReservationActorType.MEMBER,
			ReservationStatus.CONFIRMED,
			LESSON_DATE,
			START_TIME,
			LESSON_DATE.plusDays(1),
			START_TIME.plusHours(1),
			CouponAction.FREE_CHANGE_USED,
			"마감 후 일정 변경");

		assertThat(changeLog.getCouponAction()).isEqualTo(CouponAction.FREE_CHANGE_USED);
		assertThat(changeLog.getMemo()).isEqualTo("마감 후 일정 변경");
	}

	@Test
	void 예약_변경_이력은_행위자_상태_메모와_변경된_일정을_검증한다() {
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"system-subject",
				ReservationActorType.SYSTEM,
				ReservationStatus.CONFIRMED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE.plusDays(1),
				START_TIME,
				"시스템"),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_ACTOR);
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"admin-subject",
				ReservationActorType.ADMIN,
				ReservationStatus.CANCELLED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE.plusDays(1),
				START_TIME,
				"메모"),
			ExceptionCode.RESERVATION_INVALID_STATUS);
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"admin-subject",
				ReservationActorType.ADMIN,
				ReservationStatus.CONFIRMED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE,
				START_TIME,
				"메모"),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_REFERENCE);
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"admin-subject",
				ReservationActorType.ADMIN,
				ReservationStatus.CONFIRMED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE.plusDays(1),
				START_TIME,
				" "),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_MEMO);
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"member-subject",
				ReservationActorType.MEMBER,
				ReservationStatus.CONFIRMED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE.plusDays(1),
				START_TIME,
				CouponAction.RETURN,
				"잘못된 쿠폰 처리"),
			ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		assertReservationException(
			() -> ReservationChangeLog.reservationChanged(
				1L,
				"member-subject",
				ReservationActorType.MEMBER,
				ReservationStatus.CONFIRMED,
				LESSON_DATE,
				START_TIME,
				LESSON_DATE.plusDays(1),
				START_TIME,
				"가".repeat(501)),
			ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_MEMO);
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
