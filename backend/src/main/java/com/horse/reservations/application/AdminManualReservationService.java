package com.horse.reservations.application;

import java.time.Clock;
import java.time.Instant;
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
import com.horse.reservations.domain.PendingPaymentDeadlinePolicy;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationBookingTimePolicy;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class AdminManualReservationService {

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationScheduleDateLockService scheduleDateLockService;
	private final ReservationCapacityService reservationCapacityService;
	private final CouponSelectionService couponSelectionService;
	private final CouponHoldService couponHoldService;
	private final ReservationChangeLogRepository changeLogRepository;

	public AdminManualReservationService(
		Clock clock,
		MemberRepository memberRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationScheduleDateLockService scheduleDateLockService,
		ReservationCapacityService reservationCapacityService,
		CouponSelectionService couponSelectionService,
		CouponHoldService couponHoldService,
		ReservationChangeLogRepository changeLogRepository
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.scheduleDateLockService = scheduleDateLockService;
		this.reservationCapacityService = reservationCapacityService;
		this.couponSelectionService = couponSelectionService;
		this.couponHoldService = couponHoldService;
		this.changeLogRepository = changeLogRepository;
	}

	@Transactional
	public ReservationApplicationResult create(
		String adminAuthSubject,
		Long memberId,
		Long timeSlotId,
		String classType,
		String reason
	) {
		final Member member = findMember(memberId);
		final RidingClass ridingClass = parseRidingClass(classType);
		ensureEligible(member, ridingClass);
		final TimeSlotCapacity timeSlotSnapshot = findTimeSlot(timeSlotId);
		final Instant requestedAtInstant = clock.instant();
		ReservationBookingTimePolicy.ensureCanBookByAdmin(
			timeSlotSnapshot.getLessonDate(),
			timeSlotSnapshot.getStartTime(),
			requestedAtInstant);

		scheduleDateLockService.lockForReentry(timeSlotSnapshot.getLessonDate(), memberId);
		final TimeSlotCapacity timeSlot = reservationCapacityService.lockAndEnsureAvailable(
			timeSlotId,
			memberId,
			ridingClass);
		final Optional<CouponSelectionResult> selection = couponSelectionService.selectForUpdate(
			memberId,
			ridingClass,
			timeSlot.getLessonDate());
		final LocalDateTime requestedAt =
			LocalDateTime.ofInstant(requestedAtInstant, clock.getZone());
		if (selection.isEmpty()) {
			return createPendingPaymentReservation(
				timeSlot,
				memberId,
				ridingClass,
				requestedAt,
				adminAuthSubject,
				reason);
		}
		return createCouponReservation(
			timeSlot,
			memberId,
			ridingClass,
			selection.get(),
			requestedAt,
			adminAuthSubject,
			reason);
	}

	private ReservationApplicationResult createCouponReservation(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		CouponSelectionResult selection,
		LocalDateTime requestedAt,
		String adminAuthSubject,
		String reason
	) {
		final Reservation reservation = reservationCapacityService.createCouponReservation(
			timeSlot,
			memberId,
			ridingClass,
			selection.couponId(),
			requestedAt);
		final CouponHoldResult hold = couponHoldService.hold(
			selection.couponId(),
			reservation.getId(),
			memberId,
			selection.couponOwnerMemberId(),
			selection.familyGroupId(),
			timeSlot.getLessonDate(),
			requestedAt,
			CouponActorType.ADMIN);
		reservation.confirm(requestedAt);
		couponHoldService.confirm(
			reservation.getId(),
			requestedAt,
			CouponActorType.ADMIN);
		recordCreation(reservation, adminAuthSubject, reason);
		return ReservationApplicationResult.coupon(
			reservation,
			ReservationCouponResult.from(selection, hold));
	}

	private ReservationApplicationResult createPendingPaymentReservation(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		LocalDateTime requestedAt,
		String adminAuthSubject,
		String reason
	) {
		final LocalDateTime paymentDueAt = PendingPaymentDeadlinePolicy.calculate(
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			requestedAt);
		final Reservation reservation = reservationCapacityService.createSinglePaymentReservation(
			timeSlot,
			memberId,
			ridingClass,
			paymentDueAt,
			requestedAt);
		recordCreation(reservation, adminAuthSubject, reason);
		return ReservationApplicationResult.singlePayment(reservation);
	}

	private void recordCreation(
		Reservation reservation,
		String adminAuthSubject,
		String reason
	) {
		changeLogRepository.save(ReservationChangeLog.adminReservationCreated(
			reservation,
			adminAuthSubject,
			reason));
	}

	private Member findMember(Long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private TimeSlotCapacity findTimeSlot(Long timeSlotId) {
		return timeSlotRepository.findById(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

	private RidingClass parseRidingClass(String classType) {
		return Arrays.stream(RidingClass.values())
			.filter(ridingClass -> ridingClass.name().equals(classType))
			.findFirst()
			.orElseThrow(() -> new ReservationException(
				ExceptionCode.RESERVATION_INVALID_RIDING_CLASS));
	}

	private void ensureEligible(Member member, RidingClass ridingClass) {
		if (!member.availableRidingClasses().contains(ridingClass)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_RIDING_CLASS);
		}
	}
}
