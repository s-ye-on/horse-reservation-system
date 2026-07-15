package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class ReservationNoShowService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final CouponHoldService couponHoldService;

	public ReservationNoShowService(
		Clock clock,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationNoShowResult process(
		Long reservationId,
		String adminSubject,
		String requestedCouponAction,
		String memo
	) {
		final CouponAction couponAction = CouponAction.fromRequestValue(requestedCouponAction);
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final boolean changed = reservation.recordNoShow(couponAction, memo);
		if (!changed) {
			return ReservationNoShowResult.from(reservation);
		}

		final LocalDateTime processedAt = LocalDateTime.now(clock);
		processCoupon(reservation, couponAction, processedAt);
		changeLogRepository.save(ReservationChangeLog.noShowProcessed(
			reservation.getId(),
			adminSubject,
			reservation.getLessonDate(),
			reservation.getStartTime(),
			couponAction,
			memo));
		return ReservationNoShowResult.from(reservation);
	}

	private void processCoupon(
		Reservation reservation,
		CouponAction couponAction,
		LocalDateTime processedAt
	) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
		if (couponAction == CouponAction.DEDUCT) {
			couponHoldService.deduct(reservation.getId(), processedAt, CouponActorType.ADMIN);
			return;
		}
		couponHoldService.release(reservation.getId(), processedAt, CouponActorType.ADMIN);
	}
}
