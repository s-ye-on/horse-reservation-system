package com.horse.schedules.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateHorizonRepository;

@Service
public class ScheduleDateHorizonService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;

	private final Clock clock;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateHorizonRepository horizonRepository;

	public ScheduleDateHorizonService(
		Clock clock,
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateHorizonRepository horizonRepository
	) {
		this.clock = clock;
		this.configGuardRepository = configGuardRepository;
		this.horizonRepository = horizonRepository;
	}

	@Transactional
	public ScheduleDateHorizonResult ensureHorizon() {
		final ScheduleConfigGuard configGuard = configGuardRepository.findSingletonForShare();
		final LocalDate startDate = ZonedDateTime.ofInstant(clock.instant(), SEOUL_ZONE).toLocalDate();
		final LocalDate endDate = startDate.plusMonths(HORIZON_MONTHS);
		if (configGuard.getStatus() != ScheduleConfigStatus.ACTIVE) {
			return new ScheduleDateHorizonResult(
				startDate,
				endDate,
				0,
				configGuard.getActiveVersion());
		}
		final int insertedCount = horizonRepository.insertMissingRange(
			startDate,
			endDate,
			configGuard.getActiveVersion());
		return new ScheduleDateHorizonResult(
			startDate,
			endDate,
			insertedCount,
			configGuard.getActiveVersion());
	}
}
