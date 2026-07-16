package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

@ExtendWith(OutputCaptureExtension.class)
class BulkReservationAttendanceServiceTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);

	@Test
	void 항목별_업무_예외와_실행_예외를_격리하고_다음_항목을_처리한다(CapturedOutput output) {
		final BulkReservationAttendanceItemService itemService = mock(BulkReservationAttendanceItemService.class);
		final BulkReservationAttendanceService service = new BulkReservationAttendanceService(itemService);
		final List<BulkReservationAttendanceCommand> commands = List.of(
			command(1L), command(2L), command(3L), command(4L));
		when(itemService.process(LESSON_DATE, START_TIME, "admin", commands.get(0)))
			.thenReturn(BulkReservationAttendanceItemResult.success(1L, "complete", "completed"));
		when(itemService.process(LESSON_DATE, START_TIME, "admin", commands.get(1)))
			.thenThrow(new ReservationException(ExceptionCode.RESERVATION_INVALID_STATUS));
		when(itemService.process(LESSON_DATE, START_TIME, "admin", commands.get(2)))
			.thenThrow(new IllegalStateException("database unavailable"));
		when(itemService.process(LESSON_DATE, START_TIME, "admin", commands.get(3)))
			.thenReturn(BulkReservationAttendanceItemResult.success(4L, "complete", "completed"));

		final BulkReservationAttendanceResult result = service.process(
			LESSON_DATE, START_TIME, "admin", commands);

		assertThat(result.requestedCount()).isEqualTo(4);
		assertThat(result.succeededCount()).isEqualTo(2);
		assertThat(result.failedCount()).isEqualTo(2);
		assertThat(result.items()).extracting(BulkReservationAttendanceItemResult::errorCode)
			.containsExactly("", "RESERVATION_INVALID_STATUS", "COMMON_INTERNAL_ERROR", "");
		assertThat(output)
			.contains("Bulk reservation attendance item failed.")
			.contains("reservationId=3")
			.contains("action=complete")
			.contains("adminSubject=admin");
	}

	private BulkReservationAttendanceCommand command(Long reservationId) {
		return new BulkReservationAttendanceCommand(reservationId, "complete", null, null);
	}
}
