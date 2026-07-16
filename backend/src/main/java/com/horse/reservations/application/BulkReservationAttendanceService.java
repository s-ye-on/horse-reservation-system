package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.horse.global.exception.BusinessException;

@Service
public class BulkReservationAttendanceService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BulkReservationAttendanceService.class);

	private final BulkReservationAttendanceItemService itemService;

	public BulkReservationAttendanceService(BulkReservationAttendanceItemService itemService) {
		this.itemService = itemService;
	}

	public BulkReservationAttendanceResult process(
		LocalDate lessonDate,
		LocalTime startTime,
		String adminSubject,
		List<BulkReservationAttendanceCommand> commands
	) {
		final List<BulkReservationAttendanceItemResult> items = commands.stream()
			.map(command -> processItem(lessonDate, startTime, adminSubject, command))
			.toList();
		return BulkReservationAttendanceResult.from(items);
	}

	private BulkReservationAttendanceItemResult processItem(
		LocalDate lessonDate,
		LocalTime startTime,
		String adminSubject,
		BulkReservationAttendanceCommand command
	) {
		try {
			return itemService.process(lessonDate, startTime, adminSubject, command);
		}
		catch (BusinessException exception) {
			return BulkReservationAttendanceItemResult.failure(
				command.reservationId(), command.action(), exception);
		}
		catch (RuntimeException exception) {
			LOGGER.error(
				"Bulk reservation attendance item failed. reservationId={}, action={}, adminSubject={}",
				command.reservationId(),
				command.action(),
				adminSubject,
				exception);
			return BulkReservationAttendanceItemResult.unexpectedFailure(
				command.reservationId(), command.action());
		}
	}
}
