package com.horse.reservations.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.application.CouponHoldService;
import com.horse.coupons.domain.CouponActorType;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.CancellationResponsibility;
import com.horse.reservations.domain.CouponAction;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationActorType;
import com.horse.reservations.domain.ReservationCancellationDecision;
import com.horse.reservations.domain.ReservationCancellationPolicy;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;

@Service
public class MemberReservationCancellationService {

	private static final int MAX_REASON_LENGTH = 500;

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final ReservationScheduleDateLockService scheduleDateLockService;
	private final CouponHoldService couponHoldService;
	private final TimeSlotClosureCancellationService closureCancellationService;

	public MemberReservationCancellationService(
		Clock clock,
		MemberRepository memberRepository,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		ReservationScheduleDateLockService scheduleDateLockService,
		CouponHoldService couponHoldService,
		TimeSlotClosureCancellationService closureCancellationService
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.scheduleDateLockService = scheduleDateLockService;
		this.couponHoldService = couponHoldService;
		this.closureCancellationService = closureCancellationService;
	}

	@Transactional(readOnly = true)
	public ReservationCancellationPreviewResult preview(String authSubject, Long reservationId) {
		final Member member = findMember(authSubject);
		final Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		ensureOwner(reservation, member.getId());
		final Instant previewedAt = Instant.now(clock);
		reservation.validateCanCancel(LocalDateTime.ofInstant(previewedAt, clock.getZone()));
		final ReservationCancellationDecision decision = decide(reservation, previewedAt);
		return new ReservationCancellationPreviewResult(
			reservation.getId(),
			decision.timing(),
			CancellationResponsibility.MEMBER,
			decision.couponAction());
	}

	@Transactional
	public ReservationCancelResult cancel(String authSubject, Long reservationId, String reason) {
		final String normalizedReason = requireReason(reason);
		final Member member = findMember(authSubject);
		final ReservationTimeSlotProjection snapshot = reservationRepository.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final java.util.Optional<ReservationCancelResult> closureCancellation =
			closureCancellationService.cancelByMemberIfImpacted(
				reservationId,
				member.getId(),
				authSubject,
				normalizedReason);
		if (closureCancellation.isPresent()) {
			return closureCancellation.get();
		}
		scheduleDateLockService.lockForMemberCancellation(
			snapshot.getLessonDate(),
			snapshot.getMemberId());
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		reservation.ensureSchedule(snapshot.getLessonDate(), snapshot.getStartTime());
		ensureOwner(reservation, member.getId());
		if (isRepeatedMemberCancellation(reservation)) {
			return ReservationCancelResult.from(reservation, false);
		}

		final Instant cancelledInstant = Instant.now(clock);
		final ReservationCancellationDecision decision = decide(reservation, cancelledInstant);
		final ReservationStatus fromStatus = reservation.getStatus();
		final boolean changed = reservation.cancelByMember(
			LocalDateTime.ofInstant(cancelledInstant, clock.getZone()),
			decision.couponAction());
		if (changed) {
			processCoupon(reservation, decision.couponAction(), cancelledInstant);
			changeLogRepository.save(ReservationChangeLog.reservationCancelled(
				reservation,
				fromStatus,
				authSubject,
				ReservationActorType.MEMBER,
				normalizedReason));
		}
		return ReservationCancelResult.from(reservation, changed);
	}

	private ReservationCancellationDecision decide(Reservation reservation, Instant requestedAt) {
		return ReservationCancellationPolicy.evaluate(
			reservation.getLessonDate(),
			requestedAt,
			reservation.getPaymentSource(),
			CancellationResponsibility.MEMBER);
	}

	private void processCoupon(
		Reservation reservation,
		CouponAction couponAction,
		Instant cancelledInstant
	) {
		if (reservation.getPaymentSource() != PaymentSource.COUPON) {
			return;
		}
		final LocalDateTime occurredAt = LocalDateTime.ofInstant(cancelledInstant, clock.getZone());
		if (couponAction == CouponAction.DEDUCT) {
			couponHoldService.deduct(reservation.getId(), occurredAt, CouponActorType.MEMBER);
			return;
		}
		couponHoldService.release(reservation.getId(), occurredAt, CouponActorType.MEMBER);
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private void ensureOwner(Reservation reservation, Long memberId) {
		if (!reservation.getMemberId().equals(memberId)) {
			throw new ReservationException(ExceptionCode.RESERVATION_MEMBER_MISMATCH);
		}
	}

	private boolean isRepeatedMemberCancellation(Reservation reservation) {
		return reservation.getStatus() == ReservationStatus.CANCELLED
			&& reservation.getCancellationResponsibility() == CancellationResponsibility.MEMBER;
	}

	private String requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CANCELLATION_REASON);
		}
		final String normalized = reason.strip();
		if (normalized.length() > MAX_REASON_LENGTH) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_CANCELLATION_REASON);
		}
		return normalized;
	}
}
