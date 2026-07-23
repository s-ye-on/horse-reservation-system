package com.horse.schedules.presentation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.horse.schedules.application.ScheduleDateHorizonService;

@Component
public class ScheduleDateHorizonScheduler {

	private final ScheduleDateHorizonService horizonService;

	public ScheduleDateHorizonScheduler(ScheduleDateHorizonService horizonService) {
		this.horizonService = horizonService;
	}

	@Scheduled(cron = "${schedule.horizon.cron:0 5 0 * * *}", zone = "Asia/Seoul")
	public void ensureHorizon() {
		horizonService.ensureHorizon();
	}
}
