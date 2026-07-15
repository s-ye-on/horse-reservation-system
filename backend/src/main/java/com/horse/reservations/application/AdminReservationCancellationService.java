package com.horse.reservations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.CancellationResponsibility;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationCancellationDecision;
import com.horse.reservations.domain.ReservationCancellationPolicy;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class AdminReservationCancellationService {

	private static final int MAX_MEMO_LENGTH = 500;

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final CouponHoldService couponHoldService;

	public AdminReservationCancellationService(
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

	@Transactional(readOnly = true)
	public ReservationCancellationPreviewResult preview(
		Long reservationId,
		String requestedResponsibility
	) {
		final CancellationResponsibility responsibility =
			CancellationResponsibility.fromRequestValue(requestedResponsibility);
		final Reservation reservation = findReservation(reservationId);
		reservation.ensureChangeable();
		final ReservationCancellationDecision decision = ReservationCancellationPolicy.evaluate(
			reservation.getLessonDate(),
			Instant.now(clock),
			reservation.getPaymentSource(),
			responsibility);
		return new ReservationCancellationPreviewResult(
			reservation.getId(),
			decision.timing(),
			responsibility,
			decision.couponAction());
	}

	@Transactional
	public ReservationCancelResult cancel(
		Long reservationId,
		String adminSubject,
		String requestedResponsibility,
		String requestedCouponAction,
		String memo
	) {
		final String normalizedMemo = requireMemo(memo);
		final CancellationResponsibility responsibility =
			CancellationResponsibility.fromRequestValue(requestedResponsibility);
		final CouponAction couponAction = CouponAction.fromRequestValue(requestedCouponAction);
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final ReservationStatus fromStatus = reservation.getStatus();
		final boolean changed = reservation.cancelByAdmin(
			LocalDateTime.now(clock),
			responsibility,
			couponAction,
			normalizedMemo);
		if (changed) {
			processCoupon(reservation, couponAction);
			changeLogRepository.save(ReservationChangeLog.reservationCancelled(
				reservation,
				fromStatus,
				adminSubject,
				ReservationActorType.ADMIN,
				normalizedMemo));
		}
		return ReservationCancelResult.from(reservation, changed);
	}

	private Reservation findReservation(Long reservationId) {
		return reservationRepository.findById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
	}

	private void processCoupon(Reservation reservation, CouponAction couponAction) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
		final LocalDateTime occurredAt = reservation.getCancelledAt();
		if (couponAction == CouponAction.DEDUCT) {
			couponHoldService.deduct(reservation.getId(), occurredAt, CouponActorType.ADMIN);
			return;
		}
		couponHoldService.release(reservation.getId(), occurredAt, CouponActorType.ADMIN);
	}

	private String requireMemo(String memo) {
		if (memo == null || memo.isBlank() || memo.strip().length() > MAX_MEMO_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
		}
		return memo.strip();
	}
}
