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
	private static final LocalDateTime LESSON_START = LocalDateTime.of(LESSON_DATE, START_TIME);
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

		assertThat(reservation.completeRide(LESSON_START)).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
		assertThat(reservation.completeRide(LESSON_START.plusMinutes(1))).isFalse();
	}

	@Test
	void 활성_예약은_일정을_제자리에서_변경하고_동일_대상_재시도는_false를_반환한다() {
		final Reservation reservation = confirmedCouponReservation();
		final LocalDate targetLessonDate = LESSON_DATE.plusDays(1);
		final LocalTime targetStartTime = START_TIME.plusHours(1);

		assertThat(reservation.changeSchedule(targetLessonDate, targetStartTime, REQUESTED_AT)).isTrue();
		assertThat(reservation.getLessonDate()).isEqualTo(targetLessonDate);
		assertThat(reservation.getStartTime()).isEqualTo(targetStartTime);
		assertThat(reservation.changeSchedule(targetLessonDate, targetStartTime, REQUESTED_AT)).isFalse();
	}

	@Test
	void 비활성_예약은_일정을_변경할_수_없다() {
		final Reservation reservation = confirmedCouponReservation();
		reservation.completeRide(LESSON_START);

		assertReservationException(
			() -> reservation.changeSchedule(
				LESSON_DATE.plusDays(1),
				START_TIME.plusHours(1),
				LESSON_START.plusMinutes(1)),
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 일정_재검증은_기대한_일정과_다르면_실패한다() {
		final Reservation reservation = confirmedSinglePaymentReservation();

		reservation.ensureSchedule(LESSON_DATE, START_TIME);
		assertReservationException(
			() -> reservation.ensureSchedule(LESSON_DATE.plusDays(1), START_TIME),
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 확정된_쿠폰_예약은_차감이나_반환으로만_노쇼_처리한다() {
		final Reservation reservation = confirmedCouponReservation();

		assertThat(reservation.recordNoShow(
			LESSON_START,
			CouponAction.DEDUCT,
			"회원 미방문")).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
		assertThat(reservation.getCouponAction()).isEqualTo(CouponAction.DEDUCT);
		assertThat(reservation.getAdminMemo()).isEqualTo("회원 미방문");
		assertThat(reservation.recordNoShow(
			LESSON_START.plusMinutes(1),
			CouponAction.DEDUCT,
			"회원 미방문")).isFalse();
		assertReservationException(
			() -> reservation.recordNoShow(
				LESSON_START.plusMinutes(1),
				CouponAction.RETURN,
				"정책 변경"),
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 예약_결제원과_맞지_않는_노쇼_쿠폰_처리는_거부한다() {
		final Reservation couponReservation = confirmedCouponReservation();
		final Reservation singlePaymentReservation = confirmedSinglePaymentReservation();

		assertReservationException(
			() -> couponReservation.recordNoShow(LESSON_START, CouponAction.NONE, "처리 없음"),
			ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		assertReservationException(
			() -> singlePaymentReservation.recordNoShow(
				LESSON_START,
				CouponAction.DEDUCT,
				"차감"),
			ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		assertThat(singlePaymentReservation.recordNoShow(
			LESSON_START,
			CouponAction.NONE,
			"쿠폰 없음")).isTrue();
	}

	@Test
	void 회원은_활성_쿠폰_예약을_한_번만_취소한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L, RidingClass.FIRST_RIDE, LESSON_DATE, START_TIME, 10L, REQUESTED_AT);
		final LocalDateTime cancelledAt = REQUESTED_AT.plusHours(1);

		assertThat(reservation.cancelByMember(cancelledAt, CouponAction.RETURN)).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(reservation.getCancelledAt()).isEqualTo(cancelledAt);
		assertThat(reservation.getCancellationResponsibility())
			.isEqualTo(CancellationResponsibility.MEMBER);
		assertThat(reservation.getCouponAction()).isEqualTo(CouponAction.RETURN);
		assertThat(reservation.cancelByMember(cancelledAt.plusMinutes(1), CouponAction.RETURN)).isFalse();
	}

	@Test
	void 예약_결제원과_맞지_않는_회원_취소_쿠폰_처리는_거부한다() {
		final Reservation couponReservation = Reservation.createCouponPending(
			1L, RidingClass.FIRST_RIDE, LESSON_DATE, START_TIME, 10L, REQUESTED_AT);
		final Reservation singlePaymentReservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);

		assertReservationException(
			() -> couponReservation.cancelByMember(REQUESTED_AT, CouponAction.NONE),
			ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
		assertReservationException(
			() -> singlePaymentReservation.cancelByMember(REQUESTED_AT, CouponAction.DEDUCT),
			ExceptionCode.RESERVATION_INVALID_COUPON_ACTION);
	}

	@Test
	void 관리자_취소는_책임과_쿠폰_처리와_메모를_기록한다() {
		final Reservation reservation = Reservation.createCouponPending(
			1L, RidingClass.FIRST_RIDE, LESSON_DATE, START_TIME, 10L, REQUESTED_AT);

		final boolean changed = reservation.cancelByAdmin(
			REQUESTED_AT,
			CancellationResponsibility.EXCEPTION,
			CouponAction.RETURN,
			" 질병 예외 반환 ");

		assertThat(changed).isTrue();
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(reservation.getCancellationResponsibility())
			.isEqualTo(CancellationResponsibility.EXCEPTION);
		assertThat(reservation.getCouponAction()).isEqualTo(CouponAction.RETURN);
		assertThat(reservation.getAdminMemo()).isEqualTo("질병 예외 반환");
	}

	@Test
	void 관리자_취소는_같은_입력만_멱등으로_처리한다() {
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);
		reservation.cancelByAdmin(
			REQUESTED_AT,
			CancellationResponsibility.STABLE,
			CouponAction.NONE,
			"우천 취소");

		assertThat(reservation.cancelByAdmin(
			REQUESTED_AT.plusMinutes(1),
			CancellationResponsibility.STABLE,
			CouponAction.NONE,
			"우천 취소")).isFalse();
		assertReservationException(
			() -> reservation.cancelByAdmin(
				REQUESTED_AT.plusMinutes(1),
				CancellationResponsibility.MEMBER,
				CouponAction.NONE,
				"우천 취소"),
			ExceptionCode.RESERVATION_INVALID_STATUS);
	}

	@Test
	void 노쇼는_확정_예약과_오백자_이하_메모만_허용한다() {
		final Reservation pending = Reservation.createCouponPending(
			1L, RidingClass.FIRST_RIDE, LESSON_DATE, START_TIME, 10L, REQUESTED_AT);
		final Reservation confirmed = confirmedCouponReservation();

		assertReservationException(
			() -> pending.recordNoShow(LESSON_START, CouponAction.DEDUCT, "미방문"),
			ExceptionCode.RESERVATION_INVALID_STATUS);
		assertReservationException(
			() -> confirmed.recordNoShow(LESSON_START, CouponAction.DEDUCT, " "),
			ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
		assertReservationException(
			() -> confirmed.recordNoShow(
				LESSON_START,
				CouponAction.DEDUCT,
				"가".repeat(501)),
			ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
	}

	private Reservation confirmedCouponReservation() {
		final Reservation reservation = Reservation.createCouponPending(
			1L, RidingClass.FIRST_RIDE, LESSON_DATE, START_TIME, 10L, REQUESTED_AT);
		reservation.confirm(REQUESTED_AT.plusMinutes(10));
		return reservation;
	}

	private Reservation confirmedSinglePaymentReservation() {
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);
		reservation.confirm(REQUESTED_AT.plusMinutes(10));
		return reservation;
	}

	@Test
	void 확정되지_않은_예약은_기승_완료하지_않는다() {
		final Reservation pending = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LESSON_DATE,
			START_TIME,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);

		assertReservationException(
			() -> pending.completeRide(LESSON_START),
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
