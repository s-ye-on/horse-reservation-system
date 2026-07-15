package com.horse.coupons.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.CouponUsageAction;
import com.horse.coupons.domain.CouponUsageLog;
import com.horse.coupons.domain.CouponType;
import com.horse.coupons.domain.exception.CouponException;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;
import com.horse.global.exception.ExceptionCode;

@Service
public class CouponHoldService {

	private final CouponRepository couponRepository;
	private final CouponUsageLogRepository usageLogRepository;

	public CouponHoldService(
		CouponRepository couponRepository,
		CouponUsageLogRepository usageLogRepository
	) {
		this.couponRepository = couponRepository;
		this.usageLogRepository = usageLogRepository;
	}

	@Transactional
	public CouponHoldResult hold(
		Long couponId,
		Long reservationId,
		Long memberId,
		LocalDate lessonDate,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		final Coupon coupon = findCouponForUpdate(couponId);
		final Optional<CouponUsageLog> existingHold = usageLogRepository
			.findFirstByReservationIdAndActionOrderByIdAsc(reservationId, CouponUsageAction.HELD);
		if (existingHold.isPresent()) {
			ensureSameHold(existingHold.get(), couponId, memberId);
			return CouponHoldResult.from(coupon, reservationId, false);
		}

		ensureCouponMember(coupon, memberId);
		coupon.hold(lessonDate);
		usageLogRepository.save(CouponUsageLog.held(
			couponId,
			reservationId,
			memberId,
			occurredAt,
			actorType));
		return CouponHoldResult.from(coupon, reservationId, true);
	}

	@Transactional
	public boolean release(
		Long reservationId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		final Optional<CouponUsageLog> existingHold = usageLogRepository
			.findFirstByReservationIdAndActionOrderByIdAsc(reservationId, CouponUsageAction.HELD);
		if (existingHold.isEmpty()) {
			return false;
		}
		final CouponUsageLog holdLog = existingHold.get();
		final Coupon coupon = findCouponForUpdate(holdLog.getCouponId());
		if (usageLogRepository.existsByReservationIdAndAction(reservationId, CouponUsageAction.RELEASED)) {
			return false;
		}

		ensureCouponMember(coupon, holdLog.getMemberId());
		coupon.releaseHold();
		usageLogRepository.save(CouponUsageLog.released(
			coupon.getId(),
			reservationId,
			coupon.getMemberId(),
			occurredAt,
			actorType));
		return true;
	}

	@Transactional
	public boolean confirm(
		Long reservationId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		final CouponUsageLog holdLog = usageLogRepository
			.findFirstByReservationIdAndActionOrderByIdAsc(reservationId, CouponUsageAction.HELD)
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT));
		final Coupon coupon = findCouponForUpdate(holdLog.getCouponId());
		if (usageLogRepository.existsByReservationIdAndAction(
			reservationId, CouponUsageAction.CONFIRMED)) {
			return false;
		}
		if (usageLogRepository.existsByReservationIdAndAction(
			reservationId, CouponUsageAction.RELEASED)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}

		ensureCouponMember(coupon, holdLog.getMemberId());
		usageLogRepository.save(CouponUsageLog.confirmed(
			coupon.getId(),
			reservationId,
			coupon.getMemberId(),
			occurredAt,
			actorType));
		return true;
	}

	@Transactional
	public boolean use(
		Long reservationId,
		LocalDate lessonDate,
		CouponType expectedType,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		final CouponUsageLog holdLog = usageLogRepository
			.findFirstByReservationIdAndActionOrderByIdAsc(reservationId, CouponUsageAction.HELD)
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT));
		final Coupon coupon = findCouponForUpdate(holdLog.getCouponId());
		if (usageLogRepository.existsByReservationIdAndAction(reservationId, CouponUsageAction.USED)) {
			return false;
		}
		if (!usageLogRepository.existsByReservationIdAndAction(
			reservationId, CouponUsageAction.CONFIRMED)
			|| usageLogRepository.existsByReservationIdAndAction(
			reservationId, CouponUsageAction.RELEASED)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}

		ensureCouponMember(coupon, holdLog.getMemberId());
		if (coupon.getType() != expectedType) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		coupon.useHeld(lessonDate);
		usageLogRepository.save(CouponUsageLog.used(
			coupon.getId(),
			reservationId,
			coupon.getMemberId(),
			occurredAt,
			actorType));
		return true;
	}

	@Transactional
	public boolean deduct(
		Long reservationId,
		LocalDateTime occurredAt,
		CouponActorType actorType
	) {
		final CouponUsageLog holdLog = usageLogRepository
			.findFirstByReservationIdAndActionOrderByIdAsc(reservationId, CouponUsageAction.HELD)
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT));
		final Coupon coupon = findCouponForUpdate(holdLog.getCouponId());
		if (usageLogRepository.existsByReservationIdAndAction(reservationId, CouponUsageAction.DEDUCTED)) {
			return false;
		}
		if (usageLogRepository.existsByReservationIdAndAction(
			reservationId, CouponUsageAction.RELEASED)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}

		ensureCouponMember(coupon, holdLog.getMemberId());
		coupon.deductHeld();
		usageLogRepository.save(CouponUsageLog.deducted(
			coupon.getId(),
			reservationId,
			coupon.getMemberId(),
			occurredAt,
			actorType));
		return true;
	}

	private Coupon findCouponForUpdate(Long couponId) {
		return couponRepository.findByIdForUpdate(couponId)
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_NOT_FOUND));
	}

	private void ensureCouponMember(Coupon coupon, Long memberId) {
		if (!coupon.getMemberId().equals(memberId)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
	}

	private void ensureSameHold(CouponUsageLog holdLog, Long couponId, Long memberId) {
		if (!holdLog.getCouponId().equals(couponId) || !holdLog.getMemberId().equals(memberId)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
	}
}
