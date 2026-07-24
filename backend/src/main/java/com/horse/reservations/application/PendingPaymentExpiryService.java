package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;

@Service
public class PendingPaymentExpiryService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final ReservationScheduleDateLockService scheduleDateLockService;

	public PendingPaymentExpiryService(
		Clock clock,
		ReservationRepository reservationRepository,
		ReservationScheduleDateLockService scheduleDateLockService
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.scheduleDateLockService = scheduleDateLockService;
	}

	@Transactional
	public PendingPaymentExpiryResult expireDuePayments() {
		final LocalDateTime executedAt = LocalDateTime.now(clock);
		final List<Long> candidateIds = reservationRepository.findPaymentExpiryCandidateIds(
				ReservationStatus.PENDING_PAYMENT,
				executedAt,
				executedAt.toLocalDate(),
				executedAt.toLocalTime());
		int expiredCount = 0;
		for (Long reservationId : candidateIds) {
			final ReservationTimeSlotProjection snapshot = reservationRepository
				.findTimeSlotById(reservationId)
				.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
			scheduleDateLockService.lockForSafeExit(
				snapshot.getLessonDate(),
				snapshot.getMemberId());
			final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
				.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
			reservation.ensureSchedule(snapshot.getLessonDate(), snapshot.getStartTime());
			if (reservation.expirePayment(executedAt)) {
				expiredCount++;
			}
		}
		return new PendingPaymentExpiryResult(expiredCount, executedAt);
	}
}
