package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

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
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ReservationMemberDayGuardRepository;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ScheduleDateClosureCancellationService {

	private static final int MAX_MEMO_LENGTH = 500;
	private static final String RESERVATION_CANCELLED = "CLOSURE_RESERVATION_CANCELLED";

	private final Clock clock;
	private final ScheduleDateRepository scheduleDateRepository;
	private final ReservationMemberDayGuardRepository memberDayGuardRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final ScheduleAuditLogRepository auditLogRepository;
	private final CouponHoldService couponHoldService;

	public ScheduleDateClosureCancellationService(
		Clock clock,
		ScheduleDateRepository scheduleDateRepository,
		ReservationMemberDayGuardRepository memberDayGuardRepository,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		ScheduleAuditLogRepository auditLogRepository,
		CouponHoldService couponHoldService
	) {
		this.clock = clock;
		this.scheduleDateRepository = scheduleDateRepository;
		this.memberDayGuardRepository = memberDayGuardRepository;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.auditLogRepository = auditLogRepository;
		this.couponHoldService = couponHoldService;
	}

	@Transactional
	public ReservationCancelResult cancel(
		LocalDate scheduleDateValue,
		Long reservationId,
		String adminSubject,
		String memo
	) {
		final String normalizedMemo = requireMemo(memo);
		final ReservationTimeSlotProjection snapshot = reservationRepository
			.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		ensureScheduleDate(scheduleDateValue, snapshot.getLessonDate());
		final ScheduleDate scheduleDate = scheduleDateRepository
			.findByScheduleDateForUpdate(scheduleDateValue)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
		scheduleDate.ensureClosureCleanupAllowed();
		memberDayGuardRepository.acquire(snapshot.getMemberId(), scheduleDateValue);

		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		reservation.ensureSchedule(snapshot.getLessonDate(), snapshot.getStartTime());
		ensureScheduleDate(scheduleDateValue, reservation.getLessonDate());
		final ReservationStatus fromStatus = reservation.getStatus();
		final CouponAction couponAction = reservation.getPaymentSource() == PaymentSource.COUPON
			? CouponAction.RETURN
			: CouponAction.NONE;
		final LocalDateTime cancelledAt = LocalDateTime.now(clock);
		final boolean changed = reservation.cancelByAdmin(
			cancelledAt,
			CancellationResponsibility.STABLE,
			couponAction,
			normalizedMemo);
		if (!changed) {
			return ReservationCancelResult.from(reservation, false);
		}
		if (reservation.getPaymentSource() == PaymentSource.COUPON
			&& !couponHoldService.release(
				reservation.getId(),
				cancelledAt,
				CouponActorType.ADMIN)) {
			throw new CouponException(ExceptionCode.COUPON_HOLD_STATE_CONFLICT);
		}
		changeLogRepository.save(ReservationChangeLog.reservationCancelled(
			reservation,
			fromStatus,
			adminSubject,
			ReservationActorType.ADMIN,
			normalizedMemo));
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.SCHEDULE_DATE,
			scheduleDateValue.toString(),
			RESERVATION_CANCELLED,
			Map.of(
				"reservationId", reservation.getId(),
				"status", fromStatus.name()),
			Map.of(
				"reservationId", reservation.getId(),
				"status", reservation.getStatus().name()),
			adminSubject,
			normalizedMemo,
			Map.of(
				"couponAction", couponAction.name(),
				"paymentSource", reservation.getPaymentSource().name())));
		return ReservationCancelResult.from(reservation, true);
	}

	private static void ensureScheduleDate(LocalDate expected, LocalDate actual) {
		if (expected == null || !expected.equals(actual)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE);
		}
	}

	private static String requireMemo(String memo) {
		if (memo == null || memo.isBlank() || memo.strip().length() > MAX_MEMO_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_ADMIN_MEMO);
		}
		return memo.strip();
	}
}
