package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldResult;
import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.application.CouponSelectionResult;
import com.horse.coupons.application.CouponSelectionService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.exception.CouponException;
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

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final MemberRepository memberRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final CouponSelectionService couponSelectionService;
	private final ReservationCapacityService reservationCapacityService;
	private final CouponHoldService couponHoldService;

	public ReservationApplicationService(
		MemberRepository memberRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		CouponSelectionService couponSelectionService,
		ReservationCapacityService reservationCapacityService,
		CouponHoldService couponHoldService
	) {
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
		ensureFutureLessonDate(timeSlot.getLessonDate());
		final CouponSelectionResult selection = couponSelectionService
			.selectForUpdate(member.getId(), ridingClass, timeSlot.getLessonDate())
			.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_NOT_FOUND));
		final LocalDateTime requestedAt = LocalDateTime.now(SEOUL_ZONE);
		final Reservation reservation = reservationCapacityService.reserveWithCoupon(
			timeSlotId,
			member.getId(),
			ridingClass,
			selection.couponId(),
			requestedAt);
		final CouponHoldResult hold = couponHoldService.hold(
			selection.couponId(),
			reservation.getId(),
			member.getId(),
			timeSlot.getLessonDate(),
			requestedAt,
			CouponActorType.MEMBER);

		return ReservationApplicationResult.coupon(
			reservation,
			ReservationCouponResult.from(selection, hold));
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

	private void ensureFutureLessonDate(LocalDate lessonDate) {
		if (lessonDate.isBefore(LocalDate.now(SEOUL_ZONE))) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
	}
}
