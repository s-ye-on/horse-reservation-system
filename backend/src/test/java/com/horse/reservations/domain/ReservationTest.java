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
	void 승인대기_쿠폰_예약을_확정한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);
		final LocalDateTime confirmedAt = REQUESTED_AT.plusMinutes(30);

		final boolean changed = reservation.confirm(confirmedAt);

		assertThat(changed).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(reservation.getAdminConfirmedAt()).isEqualTo(confirmedAt);
		assertThat(reservation.confirm(confirmedAt.plusMinutes(1))).isFalse();
		assertThat(reservation.getAdminConfirmedAt()).isEqualTo(confirmedAt);
	}

	@Test
	void 입금대기_예약은_마감_시각_전에만_확정한다() {
		final LocalDateTime paymentDueAt = REQUESTED_AT.plusHours(2);
		final Reservation available = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);
		final Reservation expired = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);

		assertThat(available.confirm(paymentDueAt.minusNanos(1))).isTrue();
		assertReservationException(
			() -> expired.confirm(paymentDueAt),
			ExceptionCode.RESERVATION_PAYMENT_EXPIRED);
	}

	@Test
	void 승인_전_예약을_관리자_사유와_함께_반려한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);
		final LocalDateTime rejectedAt = REQUESTED_AT.plusMinutes(30);

		final boolean changed = reservation.reject(rejectedAt, "reject-admin", "입금 확인 불가");

		assertThat(changed).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
		assertThat(reservation.getRejectedAt()).isEqualTo(rejectedAt);
		assertThat(reservation.getRejectedBy()).isEqualTo("reject-admin");
		assertThat(reservation.getRejectionReason()).isEqualTo("입금 확인 불가");
		assertThat(reservation.reject(rejectedAt.plusMinutes(1), "other-admin", "다른 사유")).isFalse();
		assertThat(reservation.getRejectedBy()).isEqualTo("reject-admin");
	}

	@Test
	void 확정된_예약과_잘못된_반려_감사값은_반려하지_않는다() {
		final Reservation confirmed = Reservation.createCouponPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);
		confirmed.confirm(REQUESTED_AT.plusMinutes(10));
		final Reservation pending = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);

		assertReservationException(
			() -> confirmed.reject(REQUESTED_AT.plusMinutes(20), "reject-admin", "반려"),
			ExceptionCode.RESERVATION_INVALID_STATUS);
		assertReservationException(
			() -> pending.reject(REQUESTED_AT.plusMinutes(20), " ", "반려"),
			ExceptionCode.RESERVATION_INVALID_REJECTION_ACTOR);
		assertReservationException(
			() -> pending.reject(REQUESTED_AT.plusMinutes(20), "reject-admin", " "),
			ExceptionCode.RESERVATION_INVALID_REJECTION_REASON);
	}

	@Test
	void 입금대기_예약은_결제_마감_시각부터_만료한다() {
		final LocalDateTime paymentDueAt = REQUESTED_AT.plusHours(2);
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);

		assertThat(reservation.expirePayment(paymentDueAt.minusNanos(1))).isFalse();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(reservation.expirePayment(paymentDueAt)).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAYMENT_EXPIRED);
		assertThat(reservation.expirePayment(paymentDueAt.plusMinutes(1))).isFalse();
	}

	@Test
	void 만료된_일회_결제_예약을_확정_상태로_복구한다() {
		final LocalDateTime paymentDueAt = REQUESTED_AT.plusHours(2);
		final LocalDateTime restoredAt = paymentDueAt.plusHours(1);
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			paymentDueAt,
			REQUESTED_AT);
		reservation.expirePayment(paymentDueAt);

		reservation.restorePayment(restoredAt);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(reservation.getAdminConfirmedAt()).isEqualTo(restoredAt);
	}

	@Test
	void 만료_상태가_아니거나_복구_시각이_없으면_복구하지_않는다() {
		final Reservation pending = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);

		assertReservationException(
			() -> pending.restorePayment(REQUESTED_AT.plusHours(3)),
			ExceptionCode.RESERVATION_INVALID_STATUS);
		pending.expirePayment(REQUESTED_AT.plusHours(2));
		assertReservationException(
			() -> pending.restorePayment(null),
			ExceptionCode.RESERVATION_INVALID_PAYMENT_RESTORE_AT);
	}

	@Test
	void 확정된_일반_기승을_한_번만_완료한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L,
			RidingClass.ROUND_TROT,
			LESSON_DATE,
			START_TIME,
			10L,
			REQUESTED_AT);
		reservation.confirm(REQUESTED_AT.plusMinutes(30));

		assertThat(reservation.completeGeneralRide()).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
		assertThat(reservation.completeGeneralRide()).isFalse();
	}

	@Test
	void 특수_기승과_확정되지_않은_예약은_일반_기승으로_완료하지_않는다() {
		final Reservation special = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.DRESSAGE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);
		final Reservation pending = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);

		assertReservationException(
			special::completeGeneralRide,
			ExceptionCode.RESERVATION_INVALID_RIDING_CLASS);
		assertReservationException(
			pending::completeGeneralRide,
			ExceptionCode.RESERVATION_INVALID_STATUS);
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
