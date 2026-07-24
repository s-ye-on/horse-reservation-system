package com.horse.schedules.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ScheduleDateInflowLockService {

	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateRepository scheduleDateRepository;

	public ScheduleDateInflowLockService(
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateRepository scheduleDateRepository
	) {
		this.configGuardRepository = configGuardRepository;
		this.scheduleDateRepository = scheduleDateRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(LocalDate scheduleDateValue) {
		final ScheduleConfigGuard configGuard = configGuardRepository.findSingletonForShare();
		configGuard.ensureActive();
		final ScheduleDate scheduleDate = scheduleDateRepository
			.findByScheduleDateForUpdate(scheduleDateValue)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
		scheduleDate.ensureAppliedConfigVersion(configGuard.getActiveVersion());
		scheduleDate.ensureReservationInflowAllowed();
	}
}
