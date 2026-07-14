package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class PendingPaymentExpiryService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;

	public PendingPaymentExpiryService(Clock clock, ReservationRepository reservationRepository) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
	}

	@Transactional
	public PendingPaymentExpiryResult expireDuePayments() {
		final LocalDateTime executedAt = LocalDateTime.now(clock);
		final List<Reservation> dueReservations = reservationRepository
			.findPaymentDueReservationsForUpdate(ReservationStatus.PENDING_PAYMENT, executedAt);
		final int expiredCount = (int) dueReservations.stream()
			.filter(reservation -> reservation.expirePayment(executedAt))
			.count();
		return new PendingPaymentExpiryResult(expiredCount, executedAt);
	}
}
