package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class ReservationConfirmService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final CouponHoldService couponHoldService;

	public ReservationConfirmService(
		Clock clock,
		ReservationRepository reservationRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationConfirmResult confirm(Long reservationId) {
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final LocalDateTime confirmedAt = LocalDateTime.now(clock);
		final boolean changed = reservation.confirm(confirmedAt);
		if (changed && reservation.getPaymentSource() == PaymentSource.COUPON) {
			couponHoldService.confirm(
				reservation.getId(),
				confirmedAt,
				CouponActorType.ADMIN);
		}
		return ReservationConfirmResult.from(reservation);
	}
}
