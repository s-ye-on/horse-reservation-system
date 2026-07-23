package com.horse.schedules.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleTemplateImpactRepository;

@Service
public class RegularScheduleTemplateService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;

	private final Clock clock;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final RegularScheduleTemplateRepository templateRepository;
	private final ScheduleTemplateImpactRepository impactRepository;
	private final ScheduleAuditLogRepository auditLogRepository;

	public RegularScheduleTemplateService(
		Clock clock,
		ScheduleConfigGuardRepository configGuardRepository,
		RegularScheduleTemplateRepository templateRepository,
		ScheduleTemplateImpactRepository impactRepository,
		ScheduleAuditLogRepository auditLogRepository
	) {
		this.clock = clock;
		this.configGuardRepository = configGuardRepository;
		this.templateRepository = templateRepository;
		this.impactRepository = impactRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public List<RegularScheduleTemplateView> findAll() {
		return templateRepository.findAll().stream()
			.sorted(Comparator
				.comparing(RegularScheduleTemplate::getDayOfWeek)
				.thenComparing(RegularScheduleTemplate::getStartTime)
				.thenComparing(RegularScheduleTemplate::getId))
			.map(RegularScheduleTemplateView::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleTemplateImpactPreview preview(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		Long templateId
	) {
		if (dayOfWeek == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_DAY_OF_WEEK);
		}
		if (startTime == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_START_TIME);
		}
		final LocalDate today = today();
		return impactRepository.preview(
			dayOfWeek,
			startTime,
			today,
			today.plusMonths(HORIZON_MONTHS),
			templateId);
	}

	@Transactional
	public ScheduleTemplateMutationResult create(RegularScheduleTemplateCommand command) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		guard.ensureCanBeginSynchronization(command.expectedConfigVersion());
		final RegularScheduleTemplate template = RegularScheduleTemplate.create(
			command.dayOfWeek(),
			command.startTime(),
			command.endTime(),
			command.totalCapacity(),
			command.roundArenaCapacity(),
			command.classCapacities(),
			command.actorAuthSubject());
		ensureUnique(template.getDayOfWeek(), template.getStartTime(), null);
		final ScheduleTemplateImpactPreview impact = preview(
			template.getDayOfWeek(),
			template.getStartTime(),
			null);
		templateRepository.save(template);
		final long pendingVersion = beginSynchronization(guard, command);
		appendAudit(
			template,
			"CREATED",
			null,
			stateOf(template),
			command,
			pendingVersion,
			impact);
		return result(template, pendingVersion, impact);
	}

	@Transactional
	public ScheduleTemplateMutationResult update(
		long templateId,
		RegularScheduleTemplateCommand command
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		guard.ensureCanBeginSynchronization(command.expectedConfigVersion());
		final RegularScheduleTemplate template = findTemplate(templateId);
		final Map<String, Object> fromState = stateOf(template);
		final DayOfWeek previousDayOfWeek = template.getDayOfWeek();
		template.change(
			command.dayOfWeek(),
			command.startTime(),
			command.endTime(),
			command.totalCapacity(),
			command.roundArenaCapacity(),
			command.classCapacities(),
			command.actorAuthSubject());
		ensureUnique(template.getDayOfWeek(), template.getStartTime(), templateId);
		final ScheduleTemplateImpactPreview impact = preview(
			template.getDayOfWeek(),
			template.getStartTime(),
			templateId);
		final ScheduleTemplateImpactPreview combinedImpact = combineChangedDayImpact(
			previousDayOfWeek,
			template,
			impact);
		final long pendingVersion = beginSynchronization(guard, command);
		appendAudit(
			template,
			"UPDATED",
			fromState,
			stateOf(template),
			command,
			pendingVersion,
			combinedImpact);
		return result(template, pendingVersion, combinedImpact);
	}

	@Transactional
	public ScheduleTemplateMutationResult activate(
		long templateId,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		return changeActive(
			templateId,
			true,
			expectedConfigVersion,
			actorAuthSubject,
			reason);
	}

	@Transactional
	public ScheduleTemplateMutationResult deactivate(
		long templateId,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		return changeActive(
			templateId,
			false,
			expectedConfigVersion,
			actorAuthSubject,
			reason);
	}

	private ScheduleTemplateMutationResult changeActive(
		long templateId,
		boolean active,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		guard.ensureCanBeginSynchronization(expectedConfigVersion);
		final RegularScheduleTemplate template = findTemplate(templateId);
		final Map<String, Object> fromState = stateOf(template);
		final ScheduleTemplateImpactPreview impact = preview(
			template.getDayOfWeek(),
			template.getStartTime(),
			templateId);
		if (active) {
			template.activate(actorAuthSubject);
		}
		else {
			template.deactivate(actorAuthSubject);
		}
		final long pendingVersion = guard.beginSynchronization(
			expectedConfigVersion,
			now(),
			actorAuthSubject);
		appendAudit(
			template,
			active ? "ACTIVATED" : "DEACTIVATED",
			fromState,
			stateOf(template),
			actorAuthSubject,
			reason,
			pendingVersion,
			impact);
		return result(template, pendingVersion, impact);
	}

	private long beginSynchronization(
		ScheduleConfigGuard guard,
		RegularScheduleTemplateCommand command
	) {
		return guard.beginSynchronization(
			command.expectedConfigVersion(),
			now(),
			command.actorAuthSubject());
	}

	private void ensureUnique(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		Long excludedId
	) {
		final boolean duplicated = excludedId == null
			? templateRepository.findByDayOfWeekAndStartTime(
				dayOfWeek,
				startTime).isPresent()
			: templateRepository.existsByDayOfWeekAndStartTimeAndIdNot(
				dayOfWeek,
				startTime,
				excludedId);
		if (duplicated) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_TEMPLATE_ALREADY_EXISTS);
		}
	}

	private RegularScheduleTemplate findTemplate(long templateId) {
		return templateRepository.findById(templateId)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_TEMPLATE_NOT_FOUND));
	}

	private ScheduleTemplateMutationResult result(
		RegularScheduleTemplate template,
		long pendingVersion,
		ScheduleTemplateImpactPreview impact
	) {
		return new ScheduleTemplateMutationResult(
			RegularScheduleTemplateView.from(template),
			pendingVersion,
			impact);
	}

	private ScheduleTemplateImpactPreview combineChangedDayImpact(
		DayOfWeek previousDayOfWeek,
		RegularScheduleTemplate template,
		ScheduleTemplateImpactPreview currentImpact
	) {
		if (previousDayOfWeek == template.getDayOfWeek()) {
			return currentImpact;
		}
		final ScheduleTemplateImpactPreview previousDayImpact = preview(
			previousDayOfWeek,
			template.getStartTime(),
			template.getId());
		return new ScheduleTemplateImpactPreview(
			previousDayImpact.affectedDateCount() + currentImpact.affectedDateCount(),
			currentImpact.existingTimeSlotCount(),
			currentImpact.activeReservationCount());
	}

	private void appendAudit(
		RegularScheduleTemplate template,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		RegularScheduleTemplateCommand command,
		long pendingVersion,
		ScheduleTemplateImpactPreview impact
	) {
		appendAudit(
			template,
			action,
			fromState,
			toState,
			command.actorAuthSubject(),
			command.reason(),
			pendingVersion,
			impact);
	}

	private void appendAudit(
		RegularScheduleTemplate template,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason,
		long pendingVersion,
		ScheduleTemplateImpactPreview impact
	) {
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.TEMPLATE,
			"template:" + template.getId(),
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason,
			Map.of(
				"pendingConfigVersion", pendingVersion,
				"affectedDateCount", impact.affectedDateCount(),
				"existingTimeSlotCount", impact.existingTimeSlotCount(),
				"activeReservationCount", impact.activeReservationCount())));
	}

	private Map<String, Object> stateOf(RegularScheduleTemplate template) {
		final Map<String, Object> state = new HashMap<>();
		state.put("dayOfWeek", template.getDayOfWeek().name());
		state.put("startTime", template.getStartTime().toString());
		state.put("endTime", template.getEndTime().toString());
		state.put("totalCapacity", template.getTotalCapacity());
		state.put("roundArenaCapacity", template.getRoundArenaCapacity());
		state.put("classCapacities", template.getClassCapacities());
		state.put("active", template.isActive());
		return Map.copyOf(state);
	}

	private LocalDate today() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE).toLocalDate();
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
	}
}
