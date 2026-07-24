package com.horse.schedules.presentation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.schedules.application.ScheduleOccurrenceSynchronizationService;

@Component
public class ScheduleOccurrenceSynchronizationScheduler {

	private final ScheduleOccurrenceSynchronizationService synchronizationService;

	public ScheduleOccurrenceSynchronizationScheduler(
		ScheduleOccurrenceSynchronizationService synchronizationService
	) {
		this.synchronizationService = synchronizationService;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverPendingSynchronizationOnStartup() {
		synchronizationService.recoverPendingSynchronization();
	}

	@Scheduled(
		fixedDelayString = "${schedule.occurrence.recovery-delay-ms:300000}",
		initialDelayString = "${schedule.occurrence.recovery-initial-delay-ms:300000}")
	public void recoverPendingSynchronization() {
		synchronizationService.recoverPendingSynchronization();
	}

	@Scheduled(cron = "${schedule.occurrence.horizon-cron:0 10 0 * * *}", zone = "Asia/Seoul")
	public void synchronizeCurrentHorizon() {
		synchronizationService.synchronizeCurrentHorizon();
	}
}
