package com.horse.schedules.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.domain.exception.ScheduleException;

@Service
public class ScheduleOccurrenceSynchronizationService {

	private static final Logger LOGGER =
		LoggerFactory.getLogger(ScheduleOccurrenceSynchronizationService.class);
	private static final String FAILURE_SUMMARY = "pending schedule synchronization failed";

	private final ScheduleSynchronizationStateService stateService;
	private final ScheduleOccurrenceDateSynchronizer dateSynchronizer;
	private final ScheduleTemplateCapacityPreflight capacityPreflight;

	public ScheduleOccurrenceSynchronizationService(
		ScheduleSynchronizationStateService stateService,
		ScheduleOccurrenceDateSynchronizer dateSynchronizer,
		ScheduleTemplateCapacityPreflight capacityPreflight
	) {
		this.stateService = stateService;
		this.dateSynchronizer = dateSynchronizer;
		this.capacityPreflight = capacityPreflight;
	}

	public ScheduleOccurrenceSynchronizationResult retryPendingSynchronization(
		long expectedPendingVersion
	) {
		int createdCount = 0;
		int updatedCount = 0;
		int appliedDateCount = 0;
		int skippedDateCount = 0;
		try {
			final ScheduleSynchronizationPreparation preparation =
				stateService.prepare(expectedPendingVersion);
			if (preparation.alreadyCompleted()) {
				return result(expectedPendingVersion, 0, 0, 0, 0);
			}
			capacityPreflight.validatePendingSynchronization(expectedPendingVersion);
			for (LocalDate targetDate : preparation.horizonStart()
				.datesUntil(preparation.horizonEnd().plusDays(1))
				.toList()) {
				final ScheduleOccurrenceDateResult dateResult =
					dateSynchronizer.synchronize(expectedPendingVersion, targetDate);
				createdCount += dateResult.createdCount();
				updatedCount += dateResult.updatedCount();
				appliedDateCount += dateResult.applied() ? 1 : 0;
				skippedDateCount += dateResult.skipped() ? 1 : 0;
			}
			stateService.complete(
				expectedPendingVersion,
				preparation.horizonStart(),
				preparation.horizonEnd());
			final ScheduleOccurrenceSynchronizationResult result = result(
				expectedPendingVersion,
				createdCount,
				updatedCount,
				appliedDateCount,
				skippedDateCount);
			logCompletion(result);
			return result;
		}
		catch (RuntimeException exception) {
			final String failureCode = failureCode(exception);
			recordFailure(
				expectedPendingVersion,
				failureCode,
				FAILURE_SUMMARY);
			logFailure(expectedPendingVersion, failureCode);
			throw exception;
		}
	}

	public ScheduleOccurrenceSynchronizationResult retryCurrentSynchronization() {
		final ScheduleSynchronizationStatus status = stateService.getStatus();
		if (status.status() == ScheduleConfigStatus.ACTIVE) {
			return result(status.activeVersion(), 0, 0, 0, 0);
		}
		return retryPendingSynchronization(status.pendingVersion());
	}

	public ScheduleOccurrenceSynchronizationResult retryManualSynchronization(
		long expectedPendingVersion
	) {
		final ScheduleSynchronizationStatus status = stateService.getStatus();
		if (status.status() != ScheduleConfigStatus.SYNCING
			|| status.pendingVersion() == null
			|| status.pendingVersion() != expectedPendingVersion) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_SYNC_RETRY_NOT_ALLOWED);
		}
		return retryPendingSynchronization(expectedPendingVersion);
	}

	public ScheduleOccurrenceSynchronizationResult synchronizeCurrentHorizon() {
		final Long reconciliationVersion = capacityPreflight.beginReconciliationIfNeeded();
		if (reconciliationVersion != null) {
			return retryPendingSynchronization(reconciliationVersion);
		}
		final ScheduleSynchronizationStatus status = stateService.getStatus();
		if (status.status() == ScheduleConfigStatus.SYNCING) {
			return retryPendingSynchronization(status.pendingVersion());
		}
		final ScheduleSynchronizationPreparation preparation =
			stateService.prepareActive(status.activeVersion());
		int createdCount = 0;
		int updatedCount = 0;
		int skippedDateCount = 0;
		for (LocalDate targetDate : preparation.horizonStart()
			.datesUntil(preparation.horizonEnd().plusDays(1))
			.toList()) {
			final ScheduleOccurrenceDateResult dateResult =
				dateSynchronizer.synchronizeActive(status.activeVersion(), targetDate);
			createdCount += dateResult.createdCount();
			updatedCount += dateResult.updatedCount();
			skippedDateCount += dateResult.skipped() ? 1 : 0;
		}
		final ScheduleOccurrenceSynchronizationResult result = result(
			status.activeVersion(),
			createdCount,
			updatedCount,
			0,
			skippedDateCount);
		logCompletion(result);
		return result;
	}

	public void recoverPendingSynchronization() {
		try {
			final ScheduleSynchronizationStatus status = stateService.getStatus();
			if (status.longRunning()) {
				LOGGER.warn(
					"schedule_sync_long_running pendingVersion={} "
						+ "appliedDateCount={} totalDateCount={}",
					status.pendingVersion(),
					status.appliedDateCount(),
					status.totalDateCount());
			}
			if (status.status() == ScheduleConfigStatus.SYNCING) {
				retryPendingSynchronization(status.pendingVersion());
				return;
			}
			final Long reconciliationVersion = capacityPreflight.beginReconciliationIfNeeded();
			if (reconciliationVersion != null) {
				retryPendingSynchronization(reconciliationVersion);
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
				"schedule_sync_recovery_deferred failureCode={}",
				failureCode(exception));
		}
	}

	public ScheduleSynchronizationStatus getStatus() {
		return stateService.getStatus();
	}

	private ScheduleOccurrenceSynchronizationResult result(
		long targetVersion,
		int createdCount,
		int updatedCount,
		int appliedDateCount,
		int skippedDateCount
	) {
		return new ScheduleOccurrenceSynchronizationResult(
			targetVersion,
			createdCount,
			updatedCount,
			appliedDateCount,
			skippedDateCount,
			stateService.getStatus());
	}

	private void logCompletion(ScheduleOccurrenceSynchronizationResult result) {
		LOGGER.info(
			"schedule_sync_completed targetVersion={} createdCount={} updatedCount={} "
				+ "appliedDateCount={} skippedDateCount={}",
			result.targetVersion(),
			result.createdCount(),
			result.updatedCount(),
			result.appliedDateCount(),
			result.skippedDateCount());
	}

	private void recordFailure(
		long expectedPendingVersion,
		String failureCode,
		String failureSummary
	) {
		try {
			stateService.recordFailure(
				expectedPendingVersion,
				failureCode,
				failureSummary);
		}
		catch (RuntimeException failureRecordingException) {
			LOGGER.warn(
				"schedule_sync_failure_record_deferred pendingVersion={} failureCode={}",
				expectedPendingVersion,
				failureCode(failureRecordingException));
		}
	}

	private void logFailure(long expectedPendingVersion, String failureCode) {
		try {
			final ScheduleSynchronizationStatus failureStatus = stateService.getStatus();
			LOGGER.error(
				"schedule_sync_failed pendingVersion={} failureCode={} "
					+ "appliedDateCount={} totalDateCount={}",
				expectedPendingVersion,
				failureCode,
				failureStatus.appliedDateCount(),
				failureStatus.totalDateCount());
		}
		catch (RuntimeException statusException) {
			LOGGER.error(
				"schedule_sync_failed pendingVersion={} failureCode={} "
					+ "progressStatus=unavailable",
				expectedPendingVersion,
				failureCode);
		}
	}

	private String failureCode(RuntimeException exception) {
		if (exception instanceof BusinessException businessException) {
			return businessException.code();
		}
		return ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_FAILED.code();
	}
}
