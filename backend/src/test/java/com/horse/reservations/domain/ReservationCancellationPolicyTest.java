package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReservationCancellationPolicyTest {

	private static final LocalDate WEEKDAY_LESSON_DATE = LocalDate.of(2026, 8, 3);
	private static final Instant BEFORE_CUTOFF = Instant.parse("2026-08-02T11:59:59Z");
	private static final Instant AT_CUTOFF = Instant.parse("2026-08-02T12:00:00Z");

	@Test
	void 쿠폰_예약의_회원_취소는_마감_전_반환하고_마감부터_차감한다() {
		final ReservationCancellationDecision before = evaluate(
			BEFORE_CUTOFF,
			PaymentSource.COUPON,
			CancellationResponsibility.MEMBER);
		final ReservationCancellationDecision after = evaluate(
			AT_CUTOFF,
			PaymentSource.COUPON,
			CancellationResponsibility.MEMBER);

		assertThat(before.timing()).isEqualTo(ReservationChangeTiming.BEFORE_CUTOFF);
		assertThat(before.couponAction()).isEqualTo(CouponAction.RETURN);
		assertThat(after.timing()).isEqualTo(ReservationChangeTiming.AFTER_CUTOFF_WEEKDAY);
		assertThat(after.couponAction()).isEqualTo(CouponAction.DEDUCT);
	}

	@Test
	void 마장_책임과_예외_취소는_마감_후에도_쿠폰을_반환하도록_권장한다() {
		assertThat(evaluate(
			AT_CUTOFF,
			PaymentSource.COUPON,
			CancellationResponsibility.STABLE).couponAction()).isEqualTo(CouponAction.RETURN);
		assertThat(evaluate(
			AT_CUTOFF,
			PaymentSource.COUPON,
			CancellationResponsibility.EXCEPTION).couponAction()).isEqualTo(CouponAction.RETURN);
	}

	@Test
	void 일회_결제_예약은_마감과_책임에_관계없이_쿠폰_처리가_없다() {
		for (CancellationResponsibility responsibility : CancellationResponsibility.values()) {
			assertThat(evaluate(
				AT_CUTOFF,
				PaymentSource.SINGLE_PAYMENT,
				responsibility).couponAction()).isEqualTo(CouponAction.NONE);
		}
	}

	private ReservationCancellationDecision evaluate(
		Instant requestedAt,
		PaymentSource paymentSource,
		CancellationResponsibility responsibility
	) {
		return ReservationCancellationPolicy.evaluate(
			WEEKDAY_LESSON_DATE,
			requestedAt,
			paymentSource,
			responsibility);
	}
}
