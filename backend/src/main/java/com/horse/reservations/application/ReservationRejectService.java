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
public class ReservationRejectService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final CouponHoldService couponHoldService;

	public ReservationRejectService(
		Clock clock,
		ReservationRepository reservationRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationRejectResult reject(Long reservationId, String adminSubject, String reason) {
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final LocalDateTime rejectedAt = LocalDateTime.now(clock);
		final boolean changed = reservation.reject(rejectedAt, adminSubject, reason);
		if (changed && reservation.getPaymentSource() == PaymentSource.COUPON) {
			couponHoldService.release(
				reservation.getId(),
				rejectedAt,
				CouponActorType.ADMIN);
		}
		return ReservationRejectResult.from(reservation);
	}
}
