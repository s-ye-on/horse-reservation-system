package com.horse.schedules.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.ScheduleDateStatus;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.TimeSlotSource;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Service
public class ScheduleOccurrenceDateSynchronizer {

	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateRepository scheduleDateRepository;
	private final RegularScheduleTemplateRepository templateRepository;
	private final RecurringHolidayRuleRepository holidayRepository;
	private final TimeSlotCapacityRepository timeSlotRepository;

	public ScheduleOccurrenceDateSynchronizer(
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateRepository scheduleDateRepository,
		RegularScheduleTemplateRepository templateRepository,
		RecurringHolidayRuleRepository holidayRepository,
		TimeSlotCapacityRepository timeSlotRepository
	) {
		this.configGuardRepository = configGuardRepository;
		this.scheduleDateRepository = scheduleDateRepository;
		this.templateRepository = templateRepository;
		this.holidayRepository = holidayRepository;
		this.timeSlotRepository = timeSlotRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ScheduleOccurrenceDateResult synchronize(
		long expectedPendingVersion,
		LocalDate targetDate
	) {
		return synchronize(expectedPendingVersion, targetDate, true);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ScheduleOccurrenceDateResult synchronizeActive(
		long expectedActiveVersion,
		LocalDate targetDate
	) {
		return synchronize(expectedActiveVersion, targetDate, false);
	}

	private ScheduleOccurrenceDateResult synchronize(
		long expectedConfigVersion,
		LocalDate targetDate,
		boolean pendingSynchronization
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForShare();
		if (pendingSynchronization && guard.getActiveVersion() == expectedConfigVersion) {
			return ScheduleOccurrenceDateResult.skipped(targetDate);
		}
		if (pendingSynchronization) {
			guard.ensurePendingVersion(expectedConfigVersion);
		}
		else {
			guard.ensureActiveVersion(expectedConfigVersion);
		}
		final ScheduleDate scheduleDate = scheduleDateRepository.findByScheduleDateForUpdate(targetDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
		if (pendingSynchronization
			&& scheduleDate.getAppliedConfigVersion() == expectedConfigVersion) {
			return ScheduleOccurrenceDateResult.skipped(targetDate);
		}
		if (scheduleDate.getStatus() == ScheduleDateStatus.CLOSING
			|| scheduleDate.getStatus() == ScheduleDateStatus.CLOSED) {
			final boolean applied = scheduleDate.applyConfigVersion(expectedConfigVersion);
			return new ScheduleOccurrenceDateResult(targetDate, 0, 0, applied, false);
		}

		final List<RegularScheduleTemplate> templates =
			templateRepository.findAllByDayOfWeekOrderByStartTimeAscIdAsc(
				targetDate.getDayOfWeek());
		final Map<LocalTime, RegularScheduleTemplate> activeTemplates =
			activeTemplatesByStartTime(templates);
		final boolean recurringHoliday = scheduleDate.getStatus() != ScheduleDateStatus.OPEN
			&& holidayRepository.existsActiveOnDate(
				targetDate.getDayOfWeek(),
				targetDate);
		final List<TimeSlotCapacity> existingTimeSlots =
			timeSlotRepository.findAllByLessonDateForUpdateOrdered(targetDate);
		final Map<LocalTime, TimeSlotCapacity> existingByStartTime =
			timeSlotsByStartTime(existingTimeSlots);
		final int updatedCount = synchronizeExisting(
			existingTimeSlots,
			activeTemplates,
			recurringHoliday);
		final int createdCount = recurringHoliday
			? 0
			: createMissing(
				targetDate,
				activeTemplates,
				existingByStartTime);
		final boolean applied = scheduleDate.applyConfigVersion(expectedConfigVersion);
		timeSlotRepository.flush();
		return new ScheduleOccurrenceDateResult(
			targetDate,
			createdCount,
			updatedCount,
			applied,
			false);
	}

	private Map<LocalTime, RegularScheduleTemplate> activeTemplatesByStartTime(
		List<RegularScheduleTemplate> templates
	) {
		final Map<LocalTime, RegularScheduleTemplate> activeTemplates = new LinkedHashMap<>();
		for (RegularScheduleTemplate template : templates) {
			if (template.isActive()) {
				activeTemplates.put(template.getStartTime(), template);
			}
		}
		return Map.copyOf(activeTemplates);
	}

	private Map<LocalTime, TimeSlotCapacity> timeSlotsByStartTime(
		List<TimeSlotCapacity> timeSlots
	) {
		final Map<LocalTime, TimeSlotCapacity> byStartTime = new LinkedHashMap<>();
		for (TimeSlotCapacity timeSlot : timeSlots) {
			byStartTime.put(timeSlot.getStartTime(), timeSlot);
		}
		return Map.copyOf(byStartTime);
	}

	private int synchronizeExisting(
		List<TimeSlotCapacity> existingTimeSlots,
		Map<LocalTime, RegularScheduleTemplate> activeTemplates,
		boolean recurringHoliday
	) {
		int updatedCount = 0;
		for (TimeSlotCapacity timeSlot : existingTimeSlots) {
			if (timeSlot.getSource() != TimeSlotSource.TEMPLATE) {
				continue;
			}
			final RegularScheduleTemplate desiredTemplate =
				activeTemplates.get(timeSlot.getStartTime());
			final Long templateId = desiredTemplate == null
				? timeSlot.getTemplateId()
				: desiredTemplate.getId();
			final boolean templateInactive = desiredTemplate == null;
			final boolean changed = timeSlot.isRecurringHolidayClosed() != recurringHoliday
				|| timeSlot.isTemplateInactiveClosed() != templateInactive
				|| !timeSlot.getTemplateId().equals(templateId);
			timeSlot.synchronizeTemplateOccurrence(
				templateId,
				recurringHoliday,
				templateInactive);
			if (changed) {
				updatedCount++;
			}
		}
		return updatedCount;
	}

	private int createMissing(
		LocalDate targetDate,
		Map<LocalTime, RegularScheduleTemplate> activeTemplates,
		Map<LocalTime, TimeSlotCapacity> existingByStartTime
	) {
		int createdCount = 0;
		for (RegularScheduleTemplate template : activeTemplates.values()) {
			if (existingByStartTime.containsKey(template.getStartTime())) {
				continue;
			}
			timeSlotRepository.save(TimeSlotCapacity.createFromTemplate(
				targetDate,
				template.getId(),
				template.getStartTime(),
				template.getEndTime(),
				template.getTotalCapacity(),
				template.getRoundArenaCapacity(),
				template.getClassCapacities(),
				false));
			createdCount++;
		}
		return createdCount;
	}
}
