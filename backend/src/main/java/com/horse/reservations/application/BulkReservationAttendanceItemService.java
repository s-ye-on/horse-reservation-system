package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationAttendanceAction;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;

@Service
public class BulkReservationAttendanceItemService {

	private final ReservationRepository reservationRepository;
	private final ReservationCompletionService completionService;
	private final ReservationNoShowService noShowService;

	public BulkReservationAttendanceItemService(
		ReservationRepository reservationRepository,
		ReservationCompletionService completionService,
		ReservationNoShowService noShowService
	) {
		this.reservationRepository = reservationRepository;
		this.completionService = completionService;
		this.noShowService = noShowService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public BulkReservationAttendanceItemResult process(
		LocalDate lessonDate,
		LocalTime startTime,
		String adminSubject,
		BulkReservationAttendanceCommand command
	) {
		final ReservationAttendanceAction action = ReservationAttendanceAction.fromRequestValue(command.action());
		final ReservationTimeSlotProjection reservation = reservationRepository
			.findTimeSlotById(command.reservationId())
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		ensureSchedule(reservation, lessonDate, startTime);

		return switch (action) {
			case COMPLETE -> complete(command, action);
			case NO_SHOW -> processNoShow(command, action, adminSubject);
		};
	}

	private void ensureSchedule(
		ReservationTimeSlotProjection reservation,
		LocalDate lessonDate,
		LocalTime startTime
	) {
		if (!reservation.getLessonDate().equals(lessonDate)
			|| !reservation.getStartTime().equals(startTime)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS);
		}
	}

	private BulkReservationAttendanceItemResult complete(
		BulkReservationAttendanceCommand command,
		ReservationAttendanceAction action
	) {
		final ReservationCompletionResult result = completionService.complete(command.reservationId());
		return BulkReservationAttendanceItemResult.success(
			result.reservationId(), action.requestValue(), result.status());
	}

	private BulkReservationAttendanceItemResult processNoShow(
		BulkReservationAttendanceCommand command,
		ReservationAttendanceAction action,
		String adminSubject
	) {
		final ReservationNoShowResult result = noShowService.process(
			command.reservationId(),
			adminSubject,
			command.couponAction(),
			command.memo());
		return BulkReservationAttendanceItemResult.success(
			result.reservationId(), action.requestValue(), result.status());
	}
}
