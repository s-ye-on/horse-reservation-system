package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class ApprovalExpiryService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final CouponHoldService couponHoldService;

	public ApprovalExpiryService(
		Clock clock,
		ReservationRepository reservationRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ApprovalExpiryResult expireDueApprovals() {
		final LocalDateTime executedAt = LocalDateTime.now(clock);
		final List<Long> candidateIds = reservationRepository.findApprovalExpiryCandidateIds(
				ReservationStatus.PENDING_ADMIN_APPROVAL,
				executedAt.toLocalDate(),
				executedAt.toLocalTime());
		int expiredCount = 0;
		for (Long reservationId : candidateIds) {
			final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
			if (reservation.expireApproval(executedAt)) {
				final boolean released = couponHoldService.release(
					reservation.getId(), executedAt, CouponActorType.SYSTEM);
				if (!released) {
					throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
				}
				expiredCount++;
			}
		}
		return new ApprovalExpiryResult(expiredCount, executedAt);
	}
}
