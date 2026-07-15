package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class GeneralRideCompletionService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;
	private final CouponHoldService couponHoldService;

	public GeneralRideCompletionService(
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
	public GeneralRideCompletionResult complete(Long reservationId) {
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final boolean changed = reservation.completeGeneralRide();
		final Member member = memberRepository.findByIdForUpdate(reservation.getMemberId())
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		if (changed) {
			completeCouponUsage(reservation);
			member.increaseGeneralRideCount();
		}
		return GeneralRideCompletionResult.from(reservation, member.getGeneralRideCount());
	}

	private void completeCouponUsage(Reservation reservation) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
		final LocalDateTime completedAt = LocalDateTime.now(clock);
		couponHoldService.use(
			reservation.getId(),
			reservation.getLessonDate(),
			completedAt,
			CouponActorType.ADMIN);
	}
}
