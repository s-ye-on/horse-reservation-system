package com.horse.reservations.application;

import com.horse.reservations.domain.CancellationResponsibility;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.ReservationChangeTiming;

public record ReservationCancellationPreviewResult(
	Long reservationId,
	ReservationChangeTiming timing,
	CancellationResponsibility responsibility,
	CouponAction couponAction
) {
}
