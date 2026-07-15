package com.horse.reservations.domain;

public record ReservationCancellationDecision(
	ReservationChangeTiming timing,
	CouponAction couponAction
) {
}
