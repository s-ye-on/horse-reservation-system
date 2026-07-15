package com.horse.reservations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.CouponUsageAction;
import com.horse.coupons.domain.CouponUsageLog;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.RidingClass;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeDeadlinePolicy;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationChangeTiming;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ReservationChangeService {

	private static final int MAX_REASON_LENGTH = 500;
	private static final int RESERVATION_WINDOW_MONTHS = 3;

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final CouponRepository couponRepository;
	private final CouponUsageLogRepository couponUsageLogRepository;

	public ReservationChangeService(
		Clock clock,
		MemberRepository memberRepository,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		TimeSlotCapacityRepository timeSlotRepository,
		CouponRepository couponRepository,
		CouponUsageLogRepository couponUsageLogRepository
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.couponRepository = couponRepository;
		this.couponUsageLogRepository = couponUsageLogRepository;
	}

	@Transactional
	public ReservationChangeResult changeByMember(
		String authSubject,
		Long reservationId,
		Long targetTimeSlotId,
		String reason
	) {
		final Member member = memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		return change(
			reservationId,
			targetTimeSlotId,
			authSubject,
			ReservationActorType.MEMBER,
			member.getId(),
			normalizeOptionalReason(reason));
	}

	@Transactional
	public ReservationChangeResult changeByAdmin(
		String adminSubject,
		Long reservationId,
		Long targetTimeSlotId,
		String memo
	) {
		return change(
			reservationId,
			targetTimeSlotId,
			adminSubject,
			ReservationActorType.ADMIN,
			null,
			requireAdminMemo(memo));
	}

	private ReservationChangeResult change(
		Long reservationId,
		Long targetTimeSlotId,
		String actorAuthSubject,
		ReservationActorType actorType,
		Long expectedMemberId,
		String memo
	) {
		final ReservationTimeSlotProjection sourceProjection = reservationRepository
			.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final TimeSlotCapacity sourceTimeSlot = timeSlotRepository
			.findByLessonDateAndStartTime(sourceProjection.getLessonDate(), sourceProjection.getStartTime())
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
		final List<TimeSlotCapacity> lockedTimeSlots = timeSlotRepository.findAllByIdForUpdateOrdered(
			List.of(sourceTimeSlot.getId(), requireTargetTimeSlotId(targetTimeSlotId)));
		final TimeSlotCapacity targetTimeSlot = findLockedTimeSlot(lockedTimeSlots, targetTimeSlotId);
		findLockedTimeSlot(lockedTimeSlots, sourceTimeSlot.getId());
		final Instant changedAt = Instant.now(clock);
		final ReservationChangeTiming timing = ReservationChangeDeadlinePolicy.evaluate(
			sourceProjection.getLessonDate(),
			changedAt);
		final Coupon lockedCoupon = lockCouponForChange(sourceProjection.getCouponId());

		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		ensureExpectedMember(reservation, expectedMemberId);
		ensureSourceUnchanged(reservation, sourceProjection);
		reservation.ensureChangeable();

		if (reservation.hasSchedule(targetTimeSlot.getLessonDate(), targetTimeSlot.getStartTime())) {
			return unchangedResult(reservation);
		}

		ensureReservableLessonDate(targetTimeSlot.getLessonDate());
		if (timing != ReservationChangeTiming.BEFORE_CUTOFF) {
			ensureCouponForAfterCutoffChange(
				reservation,
				lockedCoupon,
				targetTimeSlot.getLessonDate());
			ensureTargetCapacity(targetTimeSlot, reservation.getRidingClass());
			return changeAfterCutoff(
				reservation,
				targetTimeSlot,
				lockedCoupon,
				actorAuthSubject,
				actorType,
				memo,
				changedAt);
		}
		ensureCouponValidForTarget(reservation, lockedCoupon, targetTimeSlot.getLessonDate());
		ensureTargetCapacity(targetTimeSlot, reservation.getRidingClass());

		final LocalDate sourceLessonDate = reservation.getLessonDate();
		final LocalTime sourceStartTime = reservation.getStartTime();
		final boolean changed = reservation.changeSchedule(
			targetTimeSlot.getLessonDate(),
			targetTimeSlot.getStartTime());
		if (changed) {
			reservationRepository.saveAndFlush(reservation);
			changeLogRepository.save(ReservationChangeLog.reservationChanged(
				reservation,
				actorAuthSubject,
				actorType,
				sourceLessonDate,
				sourceStartTime,
				memo));
		}
		return ReservationChangeResult.beforeCutoff(reservation, changed);
	}

	private ReservationChangeResult changeAfterCutoff(
		Reservation reservation,
		TimeSlotCapacity targetTimeSlot,
		Coupon coupon,
		String actorAuthSubject,
		ReservationActorType actorType,
		String memo,
		Instant changedAt
	) {
		if (couponUsageLogRepository.existsByReservationIdAndAction(
			reservation.getId(),
			CouponUsageAction.FREE_CHANGE_USED)) {
			throw new ReservationException(ExceptionCode.RESERVATION_CHANGE_NOT_ALLOWED);
		}

		coupon.useFreeChange();
		final LocalDate sourceLessonDate = reservation.getLessonDate();
		final LocalTime sourceStartTime = reservation.getStartTime();
		reservation.changeSchedule(targetTimeSlot.getLessonDate(), targetTimeSlot.getStartTime());
		reservationRepository.saveAndFlush(reservation);
		couponUsageLogRepository.save(CouponUsageLog.freeChangeUsed(
			coupon.getId(),
			reservation.getId(),
			reservation.getMemberId(),
			changedAt.atZone(clock.getZone()).toLocalDateTime(),
			toCouponActorType(actorType)));
		changeLogRepository.save(ReservationChangeLog.reservationChanged(
			reservation,
			actorAuthSubject,
			actorType,
			sourceLessonDate,
			sourceStartTime,
			CouponAction.FREE_CHANGE_USED,
			memo));
		return ReservationChangeResult.freeChangeUsed(reservation, true);
	}

	private void ensureTargetCapacity(TimeSlotCapacity targetTimeSlot, RidingClass ridingClass) {
		final List<Reservation> occupyingReservations = reservationRepository
			.findOccupyingByLessonDateAndStartTimeForUpdate(
				targetTimeSlot.getLessonDate(),
				targetTimeSlot.getStartTime(),
				ReservationStatus.occupyingStatuses());
		final int roundArenaOccupied = (int)occupyingReservations.stream()
			.filter(current -> TimeSlotCapacity.usesRoundArena(current.getRidingClass()))
			.count();
		final int classOccupied = (int)occupyingReservations.stream()
			.filter(current -> current.getRidingClass() == ridingClass)
			.count();
		targetTimeSlot.ensureCanReserve(
			ridingClass,
			occupyingReservations.size(),
			roundArenaOccupied,
			classOccupied);
	}

	private void ensureCouponValidForTarget(
		Reservation reservation,
		Coupon coupon,
		LocalDate targetLessonDate
	) {
		if (reservation.getCouponId() == null) {
			return;
		}
		ensureCouponReference(reservation, coupon);
		coupon.ensureUsableForLesson(targetLessonDate);
	}

	private void ensureReservableLessonDate(LocalDate lessonDate) {
		final LocalDate today = LocalDate.now(clock);
		if (lessonDate.isBefore(today) || lessonDate.isAfter(today.plusMonths(RESERVATION_WINDOW_MONTHS))) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_LESSON_DATE);
		}
	}

	private static void ensureExpectedMember(Reservation reservation, Long expectedMemberId) {
		if (expectedMemberId != null && !expectedMemberId.equals(reservation.getMemberId())) {
			throw new ReservationException(ExceptionCode.RESERVATION_MEMBER_MISMATCH);
		}
	}

	private static void ensureSourceUnchanged(
		Reservation reservation,
		ReservationTimeSlotProjection sourceProjection
	) {
		if (!reservation.hasSchedule(sourceProjection.getLessonDate(), sourceProjection.getStartTime())) {
			throw new ReservationException(ExceptionCode.RESERVATION_CHANGE_SOURCE_CONFLICT);
		}
	}

	private static TimeSlotCapacity findLockedTimeSlot(
		Collection<TimeSlotCapacity> lockedTimeSlots,
		Long timeSlotId
	) {
		return lockedTimeSlots.stream()
			.filter(timeSlot -> timeSlot.getId().equals(timeSlotId))
			.findFirst()
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}

	private static Long requireTargetTimeSlotId(Long targetTimeSlotId) {
		if (targetTimeSlotId == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND);
		}
		return targetTimeSlotId;
	}

	private static String normalizeOptionalReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return null;
		}
		final String normalized = reason.strip();
		if (normalized.length() > MAX_REASON_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_REASON);
		}
		return normalized;
	}

	private static String requireAdminMemo(String memo) {
		if (memo == null || memo.isBlank() || memo.strip().length() > MAX_REASON_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
		}
		return memo.strip();
	}

	private Coupon lockCouponForChange(Long couponId) {
		if (couponId == null) {
			return null;
		}
		return couponRepository.findByIdForUpdate(couponId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ID));
	}

	private void ensureCouponForAfterCutoffChange(
		Reservation reservation,
		Coupon coupon,
		LocalDate targetLessonDate
	) {
		if (coupon == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_CHANGE_NOT_ALLOWED);
		}
		ensureCouponReference(reservation, coupon);
		coupon.ensureUsableForLesson(targetLessonDate);
		if (coupon.isFreeChangeUsed()) {
			throw new ReservationException(ExceptionCode.RESERVATION_CHANGE_NOT_ALLOWED);
		}
	}

	private void ensureCouponReference(Reservation reservation, Coupon coupon) {
		if (coupon == null
			|| !coupon.getId().equals(reservation.getCouponId())
			|| !coupon.getMemberId().equals(reservation.getMemberId())) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_COUPON_ID);
		}
	}

	private ReservationChangeResult unchangedResult(Reservation reservation) {
		if (reservation.getCouponId() != null
			&& couponUsageLogRepository.existsByReservationIdAndAction(
				reservation.getId(),
				CouponUsageAction.FREE_CHANGE_USED)) {
			return ReservationChangeResult.freeChangeUsed(reservation, false);
		}
		return ReservationChangeResult.beforeCutoff(reservation, false);
	}

	private CouponActorType toCouponActorType(ReservationActorType actorType) {
		return switch (actorType) {
			case MEMBER -> CouponActorType.MEMBER;
			case ADMIN -> CouponActorType.ADMIN;
			default -> throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CHANGE_LOG_ACTOR);
		};
	}
}
