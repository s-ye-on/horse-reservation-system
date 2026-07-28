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
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.timeslots.application.TimeSlotClosureCommandLockService;

@Service
public class ReservationCompletionService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;
	private final CouponHoldService couponHoldService;
	private final ReservationScheduleDateLockService scheduleDateLockService;
	private final TimeSlotClosureCommandLockService closureLockService;

	public ReservationCompletionService(
		Clock clock,
		ReservationRepository reservationRepository,
		MemberRepository memberRepository,
		CouponHoldService couponHoldService,
		ReservationScheduleDateLockService scheduleDateLockService,
		TimeSlotClosureCommandLockService closureLockService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.memberRepository = memberRepository;
		this.couponHoldService = couponHoldService;
		this.scheduleDateLockService = scheduleDateLockService;
		this.closureLockService = closureLockService;
	}

	@Transactional
	public ReservationCompletionResult complete(Long reservationId) {
		final ReservationTimeSlotProjection snapshot = reservationRepository.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		scheduleDateLockService.lockDateIfPresent(snapshot.getLessonDate());
		closureLockService.ensureCommandAllowed(snapshot.getLessonDate(), snapshot.getStartTime());
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		reservation.ensureSchedule(snapshot.getLessonDate(), snapshot.getStartTime());
		final LocalDateTime completedAt = LocalDateTime.now(clock);
		final boolean changed = reservation.completeRide(completedAt);
		if (changed) {
			completeCouponUsage(reservation, completedAt);
		}
		final Member member = memberRepository.findByIdForUpdate(reservation.getMemberId())
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		if (changed) {
			increaseRideCount(member, reservation);
		}
		return ReservationCompletionResult.from(reservation, member);
	}

	private void completeCouponUsage(Reservation reservation, LocalDateTime completedAt) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
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
