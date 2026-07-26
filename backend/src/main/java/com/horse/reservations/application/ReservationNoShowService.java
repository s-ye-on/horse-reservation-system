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
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.timeslots.application.TimeSlotClosureCommandLockService;

@Service
public class ReservationNoShowService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final CouponHoldService couponHoldService;
	private final ReservationScheduleDateLockService scheduleDateLockService;
	private final TimeSlotClosureCommandLockService closureLockService;

	public ReservationNoShowService(
		Clock clock,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		CouponHoldService couponHoldService,
		ReservationScheduleDateLockService scheduleDateLockService,
		TimeSlotClosureCommandLockService closureLockService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.couponHoldService = couponHoldService;
		this.scheduleDateLockService = scheduleDateLockService;
		this.closureLockService = closureLockService;
	}

	@Transactional
	public ReservationNoShowResult process(
		Long reservationId,
		String adminSubject,
		String requestedCouponAction,
		String memo
	) {
		final CouponAction couponAction = CouponAction.fromRequestValue(requestedCouponAction);
		final ReservationTimeSlotProjection snapshot = reservationRepository.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		scheduleDateLockService.lockDateIfPresent(snapshot.getLessonDate());
		closureLockService.ensureCommandAllowed(snapshot.getLessonDate(), snapshot.getStartTime());
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		reservation.ensureSchedule(snapshot.getLessonDate(), snapshot.getStartTime());
		final LocalDateTime processedAt = LocalDateTime.now(clock);
		final boolean changed = reservation.recordNoShow(processedAt, couponAction, memo);
		if (!changed) {
			return ReservationNoShowResult.from(reservation);
		}

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
