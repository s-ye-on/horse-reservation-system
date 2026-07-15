package com.horse.reservations.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public final class ReservationCancellationPolicy {

	private ReservationCancellationPolicy() {
	}

	public static ReservationCancellationDecision evaluate(
		LocalDate lessonDate,
		Instant requestedAt,
		PaymentSource paymentSource,
		CancellationResponsibility responsibility
	) {
		if (paymentSource == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		if (responsibility == null) {
			throw new ReservationException(
				ExceptionCode.RESERVATION_INVALID_CANCELLATION_RESPONSIBILITY);
		}
		final ReservationChangeTiming timing = ReservationChangeDeadlinePolicy.evaluate(
			lessonDate,
			requestedAt);
		if (paymentSource == PaymentSource.SINGLE_PAYMENT) {
			return new ReservationCancellationDecision(timing, CouponAction.NONE);
		}
		if (responsibility != CancellationResponsibility.MEMBER
			|| timing == ReservationChangeTiming.BEFORE_CUTOFF) {
			return new ReservationCancellationDecision(timing, CouponAction.RETURN);
		}
		return new ReservationCancellationDecision(timing, CouponAction.DEDUCT);
	}
}
