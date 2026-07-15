package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.CouponType;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.RidingClass;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class ReservationCompletionService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;
	private final CouponHoldService couponHoldService;

	public ReservationCompletionService(
		Clock clock,
		ReservationRepository reservationRepository,
		MemberRepository memberRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.memberRepository = memberRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationCompletionResult complete(Long reservationId) {
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final boolean changed = reservation.completeRide();
		final Member member = memberRepository.findByIdForUpdate(reservation.getMemberId())
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		if (changed) {
			completeCouponUsage(reservation);
			increaseRideCount(member, reservation);
		}
		return ReservationCompletionResult.from(reservation, member);
	}

	private void completeCouponUsage(Reservation reservation) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
		final LocalDateTime completedAt = LocalDateTime.now(clock);
		couponHoldService.use(
			reservation.getId(),
			reservation.getLessonDate(),
			CouponType.fromRidingClass(reservation.getRidingClass()),
			completedAt,
			CouponActorType.ADMIN);
	}

	private void increaseRideCount(Member member, Reservation reservation) {
		if (reservation.getRidingClass().isGeneral()) {
			member.increaseGeneralRideCount();
		}
		else if (reservation.getRidingClass() == RidingClass.DRESSAGE) {
			member.increaseDressageRideCount();
		}
		else {
			member.increaseJumpingRideCount();
		}
	}
}
