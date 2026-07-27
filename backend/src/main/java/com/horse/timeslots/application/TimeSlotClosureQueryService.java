package com.horse.timeslots.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.application.AdminReservationQueryService;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.timeslots.domain.TimeSlotClosure;
import com.horse.timeslots.domain.exception.TimeSlotException;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;
import com.horse.timeslots.infrastructure.TimeSlotClosureImpactRepository;
import com.horse.timeslots.infrastructure.TimeSlotClosureRepository;

@Service
public class TimeSlotClosureQueryService {

	private final TimeSlotCapacityRepository timeSlotRepository;
	private final TimeSlotClosureRepository closureRepository;
	private final TimeSlotClosureImpactRepository impactRepository;
	private final ReservationRepository reservationRepository;
	private final AdminReservationQueryService reservationQueryService;

	public TimeSlotClosureQueryService(
		TimeSlotCapacityRepository timeSlotRepository,
		TimeSlotClosureRepository closureRepository,
		TimeSlotClosureImpactRepository impactRepository,
		ReservationRepository reservationRepository,
		AdminReservationQueryService reservationQueryService
	) {
		this.timeSlotRepository = timeSlotRepository;
		this.closureRepository = closureRepository;
		this.impactRepository = impactRepository;
		this.reservationRepository = reservationRepository;
		this.reservationQueryService = reservationQueryService;
	}

	@Transactional(readOnly = true)
	public TimeSlotClosureView findLatest(long timeSlotId) {
		final var timeSlot = timeSlotRepository.findById(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_NOT_FOUND));
		final TimeSlotClosure closure = closureRepository
			.findFirstByTimeSlotIdOrderByIdDesc(timeSlotId)
			.orElseThrow(() -> new TimeSlotException(ExceptionCode.TIMESLOT_CLOSURE_NOT_FOUND));
		final List<TimeSlotClosureImpactView> impacts = impactRepository
			.findAllByClosureIdOrderByReservationId(closure.getId())
			.stream()
			.map(impact -> {
				final var reservation = reservationRepository.findById(impact.getReservationId())
					.orElseThrow(() -> new TimeSlotException(
						ExceptionCode.TIMESLOT_INVALID_CLOSURE_REFERENCE));
				final boolean moved = !reservation.getLessonDate().equals(timeSlot.getLessonDate())
					|| !reservation.getStartTime().equals(timeSlot.getStartTime());
				final boolean resolved = moved
					|| !ReservationStatus.occupyingStatuses().contains(reservation.getStatus());
				return TimeSlotClosureImpactView.from(
					impact.getReservationStatusAtStart(),
					reservationQueryService.getReservation(reservation.getId()),
					resolved,
					moved);
			})
			.toList();
		final int resolvedCount = (int) impacts.stream()
			.filter(TimeSlotClosureImpactView::resolved)
			.count();
		return new TimeSlotClosureView(
			timeSlotId,
			timeSlot.isAdminClosed(),
			timeSlot.isClosed(),
			closure.getStatus(),
			closure.getReason(),
			closure.getStartedAt(),
			closure.getCompletedAt(),
			closure.getWithdrawnAt(),
			closure.getVersion(),
			impacts.size(),
			resolvedCount,
			impacts.size() - resolvedCount,
			impacts);
	}
}
