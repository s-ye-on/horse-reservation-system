package com.horse.reservations.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldResult;
import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.application.CouponSelectionResult;
import com.horse.coupons.application.CouponSelectionService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.RidingClass;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ReservationApplicationService {

	private static final Duration PAYMENT_WAIT_DURATION = Duration.ofHours(2);
	private static final int RESERVATION_WINDOW_MONTHS = 3;

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final CouponSelectionService couponSelectionService;
	private final ReservationCapacityService reservationCapacityService;
	private final CouponHoldService couponHoldService;

	public ReservationApplicationService(
		Clock clock,
		MemberRepository memberRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		CouponSelectionService couponSelectionService,
		ReservationCapacityService reservationCapacityService,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.couponSelectionService = couponSelectionService;
		this.reservationCapacityService = reservationCapacityService;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationApplicationResult apply(
		String authSubject,
		Long timeSlotId,
		String classType
	) {
		final Member member = findMember(authSubject);
		final RidingClass ridingClass = parseRidingClass(classType);
		ensureEligible(member, ridingClass);
		final TimeSlotCapacity timeSlot = lockTimeSlot(timeSlotId);
		ensureReservableLessonDate(timeSlot.getLessonDate());
		final Optional<CouponSelectionResult> selection = couponSelectionService
			.selectForUpdate(member.getId(), ridingClass, timeSlot.getLessonDate());
		final LocalDateTime requestedAt = LocalDateTime.now(clock);
		if (selection.isEmpty()) {
			return applySinglePayment(
				timeSlotId,
				member.getId(),
				ridingClass,
				requestedAt);
		}
		return applyCoupon(
			timeSlotId,
			timeSlot,
			member.getId(),
			ridingClass,
			selection.get(),
			requestedAt);
	}

	private ReservationApplicationResult applyCoupon(
		Long timeSlotId,
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		CouponSelectionResult selection,
		LocalDateTime requestedAt
	) {
		final Reservation reservation = reservationCapacityService.reserveWithCoupon(
			timeSlotId,
			memberId,
			ridingClass,
			selection.couponId(),
			requestedAt);
		final CouponHoldResult hold = couponHoldService.hold(
			selection.couponId(),
			reservation.getId(),
			memberId,
			timeSlot.getLessonDate(),
			requestedAt,
			CouponActorType.MEMBER);

		return ReservationApplicationResult.coupon(
			reservation,
			ReservationCouponResult.from(selection, hold));
	}

	private ReservationApplicationResult applySinglePayment(
		Long timeSlotId,
		Long memberId,
		RidingClass ridingClass,
		LocalDateTime requestedAt
	) {
		final Reservation reservation = reservationCapacityService.reserveWithSinglePayment(
			timeSlotId,
			memberId,
			ridingClass,
			requestedAt.plus(PAYMENT_WAIT_DURATION),
			requestedAt);
		return ReservationApplicationResult.singlePayment(reservation);
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private TimeSlotCapacity lockTimeSlot(Long timeSlotId) {
		if (timeSlotId == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND);
		}
		return timeSlotRepository.findByIdForUpdate(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

	private RidingClass parseRidingClass(String classType) {
		return Arrays.stream(RidingClass.values())
			.filter(ridingClass -> ridingClass.name().equals(classType))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_RIDING_CLASS));
	}

	private void ensureEligible(Member member, RidingClass ridingClass) {
		if (!member.availableRidingClasses().contains(ridingClass)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_RIDING_CLASS);
		}
	}

	private void ensureReservableLessonDate(LocalDate lessonDate) {
		final LocalDate today = LocalDate.now(clock);
		if (lessonDate.isBefore(today) || lessonDate.isAfter(today.plusMonths(RESERVATION_WINDOW_MONTHS))) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
	}
}
