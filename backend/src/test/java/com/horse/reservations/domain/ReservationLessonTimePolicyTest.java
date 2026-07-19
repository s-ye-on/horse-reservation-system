package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.exception.ReservationException;

class ReservationLessonTimePolicyTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 20);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);
	private static final LocalDateTime LESSON_START = LocalDateTime.of(LESSON_DATE, START_TIME);
	private static final LocalDateTime REQUESTED_AT = LESSON_START.minusDays(1);

	@Test
	void 승인_변경_취소는_수업_시작_직전까지만_허용한다() {
		final Reservation approval = couponPending();
		final Reservation change = confirmed();
		final Reservation cancellation = confirmed();

		assertThat(approval.confirm(LESSON_START.minusNanos(1))).isTrue();
		assertThat(change.changeSchedule(
			LESSON_DATE.plusDays(1),
			START_TIME,
			LESSON_START.minusNanos(1))).isTrue();
		assertThat(cancellation.cancelByMember(
			LESSON_START.minusNanos(1),
			CouponAction.RETURN)).isTrue();
	}

	@Test
	void 승인_변경_취소는_정확히_수업_시작_시각부터_거부한다() {
		final Reservation approval = couponPending();
		final Reservation change = confirmed();
		final Reservation cancellation = confirmed();

		assertReservationException(
			() -> approval.confirm(LESSON_START),
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		assertReservationException(
			() -> change.changeSchedule(LESSON_DATE.plusDays(1), START_TIME, LESSON_START),
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		assertReservationException(
			() -> cancellation.cancelByAdmin(
				LESSON_START,
				CancellationResponsibility.STABLE,
				CouponAction.RETURN,
				"수업 시작 후 취소"),
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
	}

	@Test
	void 완료와_노쇼는_수업_시작_전에는_거부한다() {
		final Reservation completion = confirmed();
		final Reservation noShow = confirmed();

		assertReservationException(
			() -> completion.completeRide(LESSON_START.minusNanos(1)),
			ExceptionCode.RESERVATION_LESSON_NOT_STARTED);
		assertReservationException(
			() -> noShow.recordNoShow(
				LESSON_START.minusNanos(1),
				CouponAction.DEDUCT,
				"미방문"),
			ExceptionCode.RESERVATION_LESSON_NOT_STARTED);
	}

	@Test
	void 완료와_노쇼는_정확히_수업_시작_시각부터_허용한다() {
		final Reservation completion = confirmed();
		final Reservation noShow = confirmed();

		assertThat(completion.completeRide(LESSON_START)).isTrue();
		assertThat(noShow.recordNoShow(
			LESSON_START,
			CouponAction.DEDUCT,
			"미방문")).isTrue();
	}

	@Test
	void 입금대기_복구도_수업_시작_이후에는_거부한다() {
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);
		reservation.expirePayment(REQUESTED_AT.plusHours(2));

		assertReservationException(
			() -> reservation.restorePayment(LESSON_START),
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
	}

	private Reservation couponPending() {
		return Reservation.createCouponPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);
	}

	private Reservation confirmed() {
		final Reservation reservation = couponPending();
		reservation.confirm(REQUESTED_AT.plusMinutes(10));
		return reservation;
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
