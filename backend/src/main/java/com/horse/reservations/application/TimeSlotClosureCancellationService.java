package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.exception.CouponException;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.CancellationResponsibility;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.timeslots.application.TimeSlotClosureCommandLockService;
import com.horse.timeslots.domain.TimeSlotClosure;
import com.horse.timeslots.infrastructure.TimeSlotClosureImpactRepository;

@Service
public class TimeSlotClosureCancellationService {

	private static final String RESERVATION_CANCELLED = "TIME_SLOT_CLOSURE_RESERVATION_CANCELLED";

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final ReservationScheduleDateLockService scheduleDateLockService;
	private final TimeSlotClosureCommandLockService closureLockService;
	private final TimeSlotClosureImpactRepository impactRepository;
	private final ScheduleAuditLogRepository auditLogRepository;
	private final CouponHoldService couponHoldService;

	public TimeSlotClosureCancellationService(
		Clock clock,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		ReservationScheduleDateLockService scheduleDateLockService,
		TimeSlotClosureCommandLockService closureLockService,
		TimeSlotClosureImpactRepository impactRepository,
		ScheduleAuditLogRepository auditLogRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.scheduleDateLockService = scheduleDateLockService;
		this.closureLockService = closureLockService;
		this.impactRepository = impactRepository;
		this.auditLogRepository = auditLogRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationCancelResult cancelByAdmin(
		Long reservationId,
		String actorSubject,
		String memo
	) {
		return cancel(
			reservationId,
			null,
			actorSubject,
			ReservationActorType.ADMIN,
			CouponActorType.ADMIN,
			memo)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS));
	}

	@Transactional
	public Optional<ReservationCancelResult> cancelByMemberIfImpacted(
		Long reservationId,
		Long memberId,
		String actorSubject,
		String reason
	) {
		final Optional<ReservationCancelResult> result = cancel(
			reservationId,
			memberId,
			actorSubject,
			ReservationActorType.MEMBER,
			CouponActorType.MEMBER,
			reason);
		return result;
	}

	private Optional<ReservationCancelResult> cancel(
		Long reservationId,
		Long expectedMemberId,
		String actorSubject,
		ReservationActorType actorType,
		CouponActorType couponActorType,
		String memo
	) {
		final ReservationTimeSlotProjection snapshot = reservationRepository.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		if (expectedMemberId != null && !snapshot.getMemberId().equals(expectedMemberId)) {
			throw new ReservationException(ExceptionCode.RESERVATION_MEMBER_MISMATCH);
		}
		scheduleDateLockService.lockForSafeExit(snapshot.getLessonDate(), snapshot.getMemberId());
		final Optional<TimeSlotClosure> closure = closureLockService.findInProgress(
			snapshot.getLessonDate(),
			snapshot.getStartTime());
		if (closure.isEmpty()
			|| !impactRepository.existsByClosureIdAndReservationId(closure.get().getId(), reservationId)) {
			return Optional.empty();
		}
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		if (!reservation.hasSchedule(snapshot.getLessonDate(), snapshot.getStartTime())
			|| !ReservationStatus.occupyingStatuses().contains(reservation.getStatus())) {
			return Optional.of(ReservationCancelResult.from(reservation, false));
		}
		final ReservationStatus fromStatus = reservation.getStatus();
		final CouponAction couponAction = reservation.getPaymentSource() == PaymentSource.COUPON
			? CouponAction.RETURN
			: CouponAction.NONE;
		final LocalDateTime occurredAt = LocalDateTime.now(clock);
		final boolean changed = reservation.cancelByAdmin(
			occurredAt,
			CancellationResponsibility.STABLE,
			couponAction,
			memo);
		if (!changed) {
			return Optional.of(ReservationCancelResult.from(reservation, false));
		}
		if (reservation.getPaymentSource() == PaymentSource.COUPON
			&& !couponHoldService.release(reservationId, occurredAt, couponActorType)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		changeLogRepository.save(ReservationChangeLog.reservationCancelled(
			reservation,
			fromStatus,
			actorSubject,
			actorType,
			memo));
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.TIME_SLOT,
			closure.get().getTimeSlotId().toString(),
			RESERVATION_CANCELLED,
			Map.of("reservationId", reservationId, "status", fromStatus.name()),
			Map.of("reservationId", reservationId, "status", reservation.getStatus().name()),
			actorSubject,
			memo,
			Map.of(
				"closureId", closure.get().getId(),
				"couponAction", couponAction.name(),
				"paymentSource", reservation.getPaymentSource().name())));
		return Optional.of(ReservationCancelResult.from(reservation, true));
	}

}
