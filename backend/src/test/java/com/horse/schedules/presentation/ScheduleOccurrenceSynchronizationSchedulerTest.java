package com.horse.schedules.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.horse.schedules.application.ScheduleOccurrenceSynchronizationService;

class ScheduleOccurrenceSynchronizationSchedulerTest {

	@Test
	void 애플리케이션_시작_완료시_미완료_동기화_복구를_즉시_요청한다() {
		final ScheduleOccurrenceSynchronizationService synchronizationService =
			mock(ScheduleOccurrenceSynchronizationService.class);
		final ScheduleOccurrenceSynchronizationScheduler scheduler =
			new ScheduleOccurrenceSynchronizationScheduler(synchronizationService);

		scheduler.recoverPendingSynchronizationOnStartup();

		verify(synchronizationService).recoverPendingSynchronization();
	}
}
