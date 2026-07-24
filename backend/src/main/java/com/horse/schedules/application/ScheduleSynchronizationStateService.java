package com.horse.schedules.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateHorizonRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ScheduleSynchronizationStateService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;

	private final Clock clock;
	private final Duration longRunningThreshold;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateHorizonRepository horizonRepository;
	private final ScheduleDateRepository scheduleDateRepository;

	public ScheduleSynchronizationStateService(
		Clock clock,
		@Value("${schedule.occurrence.long-running-threshold:PT30M}")
		Duration longRunningThreshold,
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateHorizonRepository horizonRepository,
		ScheduleDateRepository scheduleDateRepository
	) {
		this.clock = clock;
		this.longRunningThreshold = longRunningThreshold;
		this.configGuardRepository = configGuardRepository;
		this.horizonRepository = horizonRepository;
		this.scheduleDateRepository = scheduleDateRepository;
	}

	@Transactional
	ScheduleSynchronizationPreparation prepare(long expectedPendingVersion) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		final LocalDate horizonStart = today();
		final LocalDate horizonEnd = horizonStart.plusMonths(HORIZON_MONTHS);
		if (guard.getActiveVersion() == expectedPendingVersion) {
			return new ScheduleSynchronizationPreparation(
				expectedPendingVersion,
				horizonStart,
				horizonEnd,
				true);
		}
		guard.ensurePendingVersion(expectedPendingVersion);
		horizonRepository.insertMissingRange(
			horizonStart,
			horizonEnd,
			guard.getActiveVersion());
		return new ScheduleSynchronizationPreparation(
			expectedPendingVersion,
			horizonStart,
			horizonEnd,
			false);
	}

	@Transactional
	ScheduleSynchronizationPreparation prepareActive(long expectedActiveVersion) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		guard.ensureActiveVersion(expectedActiveVersion);
		final LocalDate horizonStart = today();
		final LocalDate horizonEnd = horizonStart.plusMonths(HORIZON_MONTHS);
		horizonRepository.insertMissingRange(
			horizonStart,
			horizonEnd,
			expectedActiveVersion);
		return new ScheduleSynchronizationPreparation(
			expectedActiveVersion,
			horizonStart,
			horizonEnd,
			false);
	}

	@Transactional
	void complete(
		long expectedPendingVersion,
		LocalDate horizonStart,
		LocalDate horizonEnd
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		if (guard.getActiveVersion() == expectedPendingVersion) {
			return;
		}
		guard.ensurePendingVersion(expectedPendingVersion);
		final List<ScheduleDate> lockedScheduleDates =
			scheduleDateRepository.findAllByScheduleDateBetweenForUpdate(
			horizonStart,
			horizonEnd);
		final long expectedDateCount = horizonStart.datesUntil(horizonEnd.plusDays(1)).count();
		final boolean allDatesApplied = lockedScheduleDates.stream()
			.allMatch(scheduleDate ->
				scheduleDate.getAppliedConfigVersion() == expectedPendingVersion);
		if (lockedScheduleDates.size() != expectedDateCount || !allDatesApplied) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_INCOMPLETE);
		}
		guard.completeSynchronization(expectedPendingVersion, now());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void recordFailure(
		long expectedPendingVersion,
		String failureCode,
		String failureSummary
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		guard.recordSynchronizationFailure(
			expectedPendingVersion,
			now(),
			failureCode,
			failureSummary);
	}

	@Transactional(readOnly = true)
	public ScheduleSynchronizationStatus getStatus() {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		final LocalDate horizonStart = today();
		final LocalDate horizonEnd = horizonStart.plusMonths(HORIZON_MONTHS);
		final long targetVersion = guard.getPendingVersion() == null
			? guard.getActiveVersion()
			: guard.getPendingVersion();
		final long totalDateCount = horizonStart.datesUntil(horizonEnd.plusDays(1)).count();
		final long appliedDateCount =
			scheduleDateRepository.countByScheduleDateBetweenAndAppliedConfigVersion(
				horizonStart,
				horizonEnd,
				targetVersion);
		return new ScheduleSynchronizationStatus(
			guard.getStatus(),
			guard.getActiveVersion(),
			guard.getPendingVersion(),
			horizonStart,
			horizonEnd,
			totalDateCount,
			appliedDateCount,
			guard.getSyncStartedAt(),
			guard.getLastCompletedAt(),
			guard.getLastFailedAt(),
			guard.getLastFailureCode(),
			guard.getLastFailureSummary(),
			isLongRunning(guard));
	}

	private boolean isLongRunning(ScheduleConfigGuard guard) {
		if (guard.getStatus() != ScheduleConfigStatus.SYNCING
			|| guard.getSyncStartedAt() == null) {
			return false;
		}
		final Duration elapsed = Duration.between(guard.getSyncStartedAt(), now());
		return elapsed.compareTo(longRunningThreshold) > 0;
	}

	private LocalDate today() {
		return now().toLocalDate();
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
	}
}
