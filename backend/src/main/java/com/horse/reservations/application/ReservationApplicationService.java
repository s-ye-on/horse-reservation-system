package com.horse.reservations.application;

import java.time.Clock;
import java.time.Instant;
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
import com.horse.reservations.domain.PendingPaymentDeadlinePolicy;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationBookingTimePolicy;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ReservationMemberDayGuardRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ReservationApplicationService {

	private static final int RESERVATION_WINDOW_MONTHS = 3;

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateRepository scheduleDateRepository;
	private final ReservationMemberDayGuardRepository memberDayGuardRepository;
	private final CouponSelectionService couponSelectionService;
	private final ReservationCapacityService reservationCapacityService;
	private final CouponHoldService couponHoldService;

	public ReservationApplicationService(
		Clock clock,
		MemberRepository memberRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateRepository scheduleDateRepository,
		ReservationMemberDayGuardRepository memberDayGuardRepository,
		CouponSelectionService couponSelectionService,
		ReservationCapacityService reservationCapacityService,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.configGuardRepository = configGuardRepository;
		this.scheduleDateRepository = scheduleDateRepository;
		this.memberDayGuardRepository = memberDayGuardRepository;
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
		final TimeSlotCapacity timeSlotSnapshot = findTimeSlot(timeSlotId);
		ensureReservableLessonDate(timeSlotSnapshot.getLessonDate());
		final Instant bookingRequestedAt = clock.instant();
		ReservationBookingTimePolicy.ensureCanBook(
			timeSlotSnapshot.getLessonDate(),
			timeSlotSnapshot.getStartTime(),
			bookingRequestedAt);
		lockReservationContext(member.getId(), timeSlotSnapshot.getLessonDate());
		final TimeSlotCapacity timeSlot = reservationCapacityService.lockAndEnsureAvailable(
			timeSlotId,
			member.getId(),
			ridingClass);
		final Optional<CouponSelectionResult> selection = couponSelectionService
			.selectForUpdate(member.getId(), ridingClass, timeSlot.getLessonDate());
		final LocalDateTime requestedAt = LocalDateTime.ofInstant(bookingRequestedAt, clock.getZone());
		if (selection.isEmpty()) {
			return applySinglePayment(
				timeSlot,
				member.getId(),
				ridingClass,
				requestedAt);
		}
		return applyCoupon(
			timeSlot,
			member.getId(),
			ridingClass,
			selection.get(),
			requestedAt);
	}

	private ReservationApplicationResult applyCoupon(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		CouponSelectionResult selection,
		LocalDateTime requestedAt
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
			timeSlot.getLessonDate(),
			requestedAt,
			CouponActorType.MEMBER);

		return ReservationApplicationResult.coupon(
			reservation,
			ReservationCouponResult.from(selection, hold));
	}

	private ReservationApplicationResult applySinglePayment(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		LocalDateTime requestedAt
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
		return ReservationApplicationResult.singlePayment(reservation);
	}

	private void lockReservationContext(Long memberId, LocalDate lessonDate) {
		final ScheduleConfigGuard configGuard = configGuardRepository.findSingletonForShare();
		configGuard.ensureActive();
		final ScheduleDate scheduleDate = scheduleDateRepository.findByScheduleDateForUpdate(lessonDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_INCOMPLETE));
		scheduleDate.ensureAppliedConfigVersion(configGuard.getActiveVersion());
		memberDayGuardRepository.acquire(memberId, lessonDate);
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private TimeSlotCapacity findTimeSlot(Long timeSlotId) {
		if (timeSlotId == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND);
		}
		return timeSlotRepository.findById(timeSlotId)
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
