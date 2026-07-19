package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;

class ReservationActionAvailabilityTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 20);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);
	private static final LocalDateTime LESSON_START = LocalDateTime.of(LESSON_DATE, START_TIME);
	private static final LocalDateTime REQUESTED_AT = LESSON_START.minusDays(1);

	@Test
	void 수업_전_확정_예약은_변경과_취소만_허용한다() {
		final Reservation reservation = confirmed();
		final LocalDateTime actionAt = LESSON_START.minusNanos(1);

		assertThat(reservation.displayGroupAt(actionAt)).isEqualTo(ReservationDisplayGroup.UPCOMING);
		assertAllowed(reservation, ReservationAction.CHANGE, actionAt);
		assertAllowed(reservation, ReservationAction.CANCEL, actionAt);
		assertBlocked(
			reservation, ReservationAction.COMPLETE, actionAt, ExceptionCode.RESERVATION_LESSON_NOT_STARTED);
		assertBlocked(
			reservation, ReservationAction.NO_SHOW, actionAt, ExceptionCode.RESERVATION_LESSON_NOT_STARTED);
		assertBlocked(
			reservation, ReservationAction.APPROVE, actionAt, ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 수업_시작_시각의_확정_예약은_완료와_노쇼만_허용한다() {
		final Reservation reservation = confirmed();

		assertThat(reservation.displayGroupAt(LESSON_START)).isEqualTo(ReservationDisplayGroup.PAST);
		assertBlocked(
			reservation,
			ReservationAction.CHANGE,
			LESSON_START,
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		assertBlocked(
			reservation,
			ReservationAction.CANCEL,
			LESSON_START,
			ExceptionCode.RESERVATION_LESSON_ALREADY_STARTED);
		assertAllowed(reservation, ReservationAction.COMPLETE, LESSON_START);
		assertAllowed(reservation, ReservationAction.NO_SHOW, LESSON_START);
		assertBlocked(
			reservation,
			ReservationAction.APPROVE,
			LESSON_START,
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 수업_전_승인대기_예약은_승인과_변경과_취소를_허용한다() {
		final Reservation reservation = couponPending();

		assertAllowed(reservation, ReservationAction.APPROVE, REQUESTED_AT);
		assertAllowed(reservation, ReservationAction.CHANGE, REQUESTED_AT);
		assertAllowed(reservation, ReservationAction.CANCEL, REQUESTED_AT);
		assertBlocked(
			reservation,
			ReservationAction.COMPLETE,
			REQUESTED_AT,
			ExceptionCode.RESERVATION_INVALID_STATUS);
		assertBlocked(
			reservation,
			ReservationAction.NO_SHOW,
			REQUESTED_AT,
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 입금_마감에_도달한_예약의_승인은_만료_사유를_반환한다() {
		final LocalDateTime paymentDueAt = REQUESTED_AT.plusHours(2);
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);

		assertBlocked(
			reservation,
			ReservationAction.APPROVE,
			paymentDueAt,
			ExceptionCode.RESERVATION_PAYMENT_EXPIRED);
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

	private void assertAllowed(
		Reservation reservation,
		ReservationAction action,
		LocalDateTime actionAt
	) {
		assertThat(reservation.actionAvailability(action, actionAt))
			.isEqualTo(ReservationActionAvailability.allow());
	}

	private void assertBlocked(
		Reservation reservation,
		ReservationAction action,
		LocalDateTime actionAt,
		ExceptionCode exceptionCode
	) {
		assertThat(reservation.actionAvailability(action, actionAt))
			.isEqualTo(ReservationActionAvailability.block(exceptionCode.code()));
	}
}
