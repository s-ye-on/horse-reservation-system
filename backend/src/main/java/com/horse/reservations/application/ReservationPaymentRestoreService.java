package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationChangeLog;
import com.horse.reservations.domain.ReservationChangeType;
import com.horse.reservations.domain.ReservationIntervalOverlapPolicy;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ReservationPaymentRestoreService {

	private final Clock clock;
	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationChangeLogRepository changeLogRepository;
	private final ReservationScheduleDateLockService scheduleDateLockService;

	public ReservationPaymentRestoreService(
		Clock clock,
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationRepository reservationRepository,
		ReservationChangeLogRepository changeLogRepository,
		ReservationScheduleDateLockService scheduleDateLockService
	) {
		this.clock = clock;
		this.timeSlotRepository = timeSlotRepository;
		this.reservationRepository = reservationRepository;
		this.changeLogRepository = changeLogRepository;
		this.scheduleDateLockService = scheduleDateLockService;
	}

	@Transactional
	public ReservationPaymentRestoreResult restore(Long reservationId, String adminSubject, String memo) {
		final ReservationTimeSlotProjection requestedTimeSlot = reservationRepository.findTimeSlotById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		scheduleDateLockService.lockForReentry(
			requestedTimeSlot.getLessonDate(),
			requestedTimeSlot.getMemberId());
		final TimeSlotCapacity timeSlot = timeSlotRepository.findByLessonDateAndStartTimeForUpdate(
				requestedTimeSlot.getLessonDate(), requestedTimeSlot.getStartTime())
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
		if (timeSlot.isAdminClosed()) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED);
		}
		final List<Reservation> overlaps = reservationRepository.findActiveOverlapsForUpdate(
			requestedTimeSlot.getMemberId(),
			timeSlot.getLessonDate(),
			timeSlot.getStartTime(),
			timeSlot.getEndTime());
		ReservationIntervalOverlapPolicy.ensureNoOverlap(overlaps.stream()
			.filter(current -> !current.getId().equals(reservationId))
			.toList());
		final List<Reservation> occupyingReservations =
			reservationRepository.findOccupyingByLessonDateAndStartTimeForUpdate(
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				ReservationStatus.occupyingStatuses());
		final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));

		ensureSameTimeSlot(requestedTimeSlot, reservation);
		if (isAlreadyRestored(reservation)) {
			return ReservationPaymentRestoreResult.from(reservation);
		}

		reservation.ensurePaymentRestorable();
		ensureCapacity(timeSlot, reservation, occupyingReservations);
		final LocalDateTime restoredAt = LocalDateTime.now(clock);
		reservation.restorePayment(restoredAt);
		changeLogRepository.save(ReservationChangeLog.paymentRestored(
			reservation.getId(),
			adminSubject,
			reservation.getLessonDate(),
			reservation.getStartTime(),
			memo));
		return ReservationPaymentRestoreResult.from(reservation);
	}

	private boolean isAlreadyRestored(Reservation reservation) {
		if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
			return false;
		}
		if (changeLogRepository.findByReservationIdAndChangeTypeForShare(
			reservation.getId(), ReservationChangeType.PAYMENT_RESTORED).isEmpty()) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
		return true;
	}

	private void ensureCapacity(
		TimeSlotCapacity timeSlot,
		Reservation reservation,
		List<Reservation> occupyingReservations
	) {
		final RidingClass ridingClass = reservation.getRidingClass();
		final int roundArenaOccupied = (int)occupyingReservations.stream()
			.filter(current -> TimeSlotCapacity.usesRoundArena(current.getRidingClass()))
			.count();
		final int classOccupied = (int)occupyingReservations.stream()
			.filter(current -> current.getRidingClass() == ridingClass)
			.count();
		timeSlot.ensureCanReserve(
			ridingClass,
			occupyingReservations.size(),
			roundArenaOccupied,
			classOccupied);
	}

	private void ensureSameTimeSlot(ReservationTimeSlotProjection requestedTimeSlot, Reservation locked) {
		if (!requestedTimeSlot.getLessonDate().equals(locked.getLessonDate())
			|| !requestedTimeSlot.getStartTime().equals(locked.getStartTime())) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
	}
}
