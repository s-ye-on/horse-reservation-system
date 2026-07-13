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

class ReservationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 20);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 7, 14, 10, 0);

	@Test
	void 쿠폰_예약은_관리자_승인대기_상태로_생성한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L,
			RidingClass.ROUND_BEGINNER,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_ADMIN_APPROVAL);
		assertThat(reservation.getPaymentSource()).isEqualTo(PaymentSource.COUPON);
		assertThat(reservation.getCouponId()).isEqualTo(10L);
		assertThat(reservation.getPaymentDueAt()).isNull();
	}

	@Test
	void 일회_결제_예약은_입금대기_상태로_생성한다() {
		final LocalDateTime paymentDueAt = REQUESTED_AT.plusHours(2);

		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.ROUND_BEGINNER,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(reservation.getPaymentSource()).isEqualTo(PaymentSource.SINGLE_PAYMENT);
		assertThat(reservation.getCouponId()).isNull();
		assertThat(reservation.getPaymentDueAt()).isEqualTo(paymentDueAt);
	}

	@Test
	void 예약_생성에_필요한_값을_검증한다() {
		assertReservationException(
			() -> Reservation.createCouponPending(
				null,
				RidingClass.ROUND_BEGINNER,
				LESSON_DATE,
				START_TIME,
				10L,
				REQUESTED_AT),
			ExceptionCode.RESERVATION_INVALID_MEMBER_ID);
		assertReservationException(
			() -> Reservation.createCouponPending(
				1L,
				RidingClass.ROUND_BEGINNER,
				LESSON_DATE,
				START_TIME,
				null,
				REQUESTED_AT),
			ExceptionCode.RESERVATION_INVALID_COUPON_ID);
		assertReservationException(
			() -> Reservation.createSinglePaymentPending(
				1L,
				RidingClass.ROUND_BEGINNER,
				LESSON_DATE,
				START_TIME,
				null,
				REQUESTED_AT),
			ExceptionCode.RESERVATION_INVALID_PAYMENT_DUE_AT);
	}

	@Test
	void 활성_점유_상태는_승인대기_입금대기_확정이다() {
		assertThat(ReservationStatus.occupyingStatuses())
			.containsExactlyInAnyOrder(
				ReservationStatus.PENDING_ADMIN_APPROVAL,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED);
		assertThat(ReservationStatus.values())
			.filteredOn(ReservationStatus::occupiesCapacity)
			.containsExactlyInAnyOrderElementsOf(ReservationStatus.occupyingStatuses());
	}

	private void assertReservationException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}
}
