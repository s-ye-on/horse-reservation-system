package com.horse.reservations.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationIntervalOverlapPolicy;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationConstraintViolationTranslator;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ReservationCapacityService {

	private final TimeSlotCapacityRepository timeSlotRepository;
	private final ReservationRepository reservationRepository;

	public ReservationCapacityService(
		TimeSlotCapacityRepository timeSlotRepository,
		ReservationRepository reservationRepository
	) {
		this.timeSlotRepository = timeSlotRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TimeSlotCapacity lockAndEnsureAvailable(
		Long timeSlotId,
		Long memberId,
		RidingClass ridingClass
	) {
		final TimeSlotCapacity timeSlot = getTimeSlotForUpdate(timeSlotId);
		final List<Reservation> overlappingReservations =
			reservationRepository.findActiveOverlapsForUpdate(
				memberId,
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				timeSlot.getEndTime());
		ReservationIntervalOverlapPolicy.ensureNoOverlap(overlappingReservations);
		final List<Reservation> occupyingReservations =
			reservationRepository.findOccupyingByLessonDateAndStartTimeForUpdate(
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				ReservationStatus.occupyingStatuses());
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
		return timeSlot;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Reservation createCouponReservation(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		Long couponId,
		LocalDateTime approvalRequestedAt
	) {
		try {
			return reservationRepository.saveAndFlush(Reservation.createCouponPending(
				memberId,
				ridingClass,
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				couponId,
				approvalRequestedAt));
		}
		catch (DataIntegrityViolationException exception) {
			throw ReservationConstraintViolationTranslator.translate(exception);
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Reservation createSinglePaymentReservation(
		TimeSlotCapacity timeSlot,
		Long memberId,
		RidingClass ridingClass,
		LocalDateTime paymentDueAt,
		LocalDateTime approvalRequestedAt
	) {
		try {
			return reservationRepository.saveAndFlush(Reservation.createSinglePaymentPending(
				memberId,
				ridingClass,
				timeSlot.getLessonDate(),
				timeSlot.getStartTime(),
				paymentDueAt,
				approvalRequestedAt));
		}
		catch (DataIntegrityViolationException exception) {
			throw ReservationConstraintViolationTranslator.translate(exception);
		}
	}

	private TimeSlotCapacity getTimeSlotForUpdate(Long timeSlotId) {
		if (timeSlotId == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND);
		}
		return timeSlotRepository.findByIdForUpdate(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
	}
}
