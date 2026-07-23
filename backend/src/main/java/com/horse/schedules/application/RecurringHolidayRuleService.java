package com.horse.schedules.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.RecurringHolidayRule;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RecurringHolidayImpactRepository;
import com.horse.schedules.infrastructure.RecurringHolidayImpactRepository.HolidayPeriod;
import com.horse.schedules.infrastructure.RecurringHolidayRuleRepository;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;

@Service
public class RecurringHolidayRuleService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;
	private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

	private final Clock clock;
	private final ScheduleConfigGuardRepository configGuardRepository;
	private final RecurringHolidayRuleRepository ruleRepository;
	private final RecurringHolidayImpactRepository impactRepository;
	private final ScheduleAuditLogRepository auditLogRepository;

	public RecurringHolidayRuleService(
		Clock clock,
		ScheduleConfigGuardRepository configGuardRepository,
		RecurringHolidayRuleRepository ruleRepository,
		RecurringHolidayImpactRepository impactRepository,
		ScheduleAuditLogRepository auditLogRepository
	) {
		this.clock = clock;
		this.configGuardRepository = configGuardRepository;
		this.ruleRepository = ruleRepository;
		this.impactRepository = impactRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public List<RecurringHolidayRuleView> findAll() {
		return ruleRepository.findAll().stream()
			.sorted(Comparator
				.comparing(RecurringHolidayRule::getDayOfWeek)
				.thenComparing(RecurringHolidayRule::getEffectiveFrom)
				.thenComparing(RecurringHolidayRule::getId))
			.map(RecurringHolidayRuleView::from)
			.toList();
	}

	@Transactional
	public RecurringHolidayMutationResult create(RecurringHolidayRuleCommand command) {
		final ScheduleConfigGuard guard = lockConfigGuard(command.expectedConfigVersion());
		final RecurringHolidayRule rule = RecurringHolidayRule.create(
			command.dayOfWeek(),
			command.effectiveFrom(),
			command.effectiveTo(),
			command.holidayReason(),
			command.actorAuthSubject());
		ensureNoActiveOverlap(rule, null);
		final HolidayPeriod period = periodOf(rule);
		final RecurringHolidayImpactPreview impact = preview(null, period);
		ruleRepository.save(rule);
		final long pendingVersion = beginSynchronization(guard, command);
		appendAudit(
			rule,
			"CREATED",
			null,
			stateOf(rule),
			command.actorAuthSubject(),
			command.changeReason(),
			pendingVersion,
			impact);
		return result(rule, pendingVersion, impact);
	}

	@Transactional
	public RecurringHolidayMutationResult update(
		long ruleId,
		RecurringHolidayRuleCommand command
	) {
		final ScheduleConfigGuard guard = lockConfigGuard(command.expectedConfigVersion());
		final RecurringHolidayRule rule = findRule(ruleId);
		final Map<String, Object> fromState = stateOf(rule);
		final HolidayPeriod previousPeriod = periodOf(rule);
		rule.change(
			command.dayOfWeek(),
			command.effectiveFrom(),
			command.effectiveTo(),
			command.holidayReason(),
			command.actorAuthSubject());
		if (rule.isActive()) {
			ensureNoActiveOverlap(rule, ruleId);
		}
		final RecurringHolidayImpactPreview impact = preview(previousPeriod, periodOf(rule));
		final long pendingVersion = beginSynchronization(guard, command);
		appendAudit(
			rule,
			"UPDATED",
			fromState,
			stateOf(rule),
			command.actorAuthSubject(),
			command.changeReason(),
			pendingVersion,
			impact);
		return result(rule, pendingVersion, impact);
	}

	@Transactional
	public RecurringHolidayMutationResult activate(
		long ruleId,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		return changeActive(
			ruleId,
			true,
			expectedConfigVersion,
			actorAuthSubject,
			reason);
	}

	@Transactional
	public RecurringHolidayMutationResult deactivate(
		long ruleId,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		return changeActive(
			ruleId,
			false,
			expectedConfigVersion,
			actorAuthSubject,
			reason);
	}

	private RecurringHolidayMutationResult changeActive(
		long ruleId,
		boolean active,
		long expectedConfigVersion,
		String actorAuthSubject,
		String reason
	) {
		final ScheduleConfigGuard guard = lockConfigGuard(expectedConfigVersion);
		final RecurringHolidayRule rule = findRule(ruleId);
		final Map<String, Object> fromState = stateOf(rule);
		final HolidayPeriod period = periodOf(rule);
		if (active) {
			rule.activate(actorAuthSubject);
			ensureNoActiveOverlap(rule, ruleId);
		}
		else {
			rule.deactivate(actorAuthSubject);
		}
		final RecurringHolidayImpactPreview impact = active
			? preview(null, period)
			: preview(period, null);
		final long pendingVersion = guard.beginSynchronization(
			expectedConfigVersion,
			now(),
			actorAuthSubject);
		appendAudit(
			rule,
			active ? "ACTIVATED" : "DEACTIVATED",
			fromState,
			stateOf(rule),
			actorAuthSubject,
			reason,
			pendingVersion,
			impact);
		return result(rule, pendingVersion, impact);
	}

	private ScheduleConfigGuard lockConfigGuard(long expectedConfigVersion) {
		final ScheduleConfigGuard guard = configGuardRepository.findSingletonForUpdate();
		guard.ensureCanBeginSynchronization(expectedConfigVersion);
		return guard;
	}

	private long beginSynchronization(
		ScheduleConfigGuard guard,
		RecurringHolidayRuleCommand command
	) {
		return guard.beginSynchronization(
			command.expectedConfigVersion(),
			now(),
			command.actorAuthSubject());
	}

	private void ensureNoActiveOverlap(RecurringHolidayRule rule, Long excludedId) {
		final LocalDate candidateEffectiveTo = maximumDate(rule.getEffectiveTo());
		if (ruleRepository.existsActiveOverlap(
			rule.getDayOfWeek(),
			rule.getEffectiveFrom(),
			candidateEffectiveTo,
			MAXIMUM_DATE,
			excludedId)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_RECURRING_HOLIDAY_OVERLAP);
		}
	}

	private RecurringHolidayRule findRule(long ruleId) {
		return ruleRepository.findById(ruleId)
			.orElseThrow(() -> new ScheduleException(
				ExceptionCode.SCHEDULE_RECURRING_HOLIDAY_NOT_FOUND));
	}

	private RecurringHolidayImpactPreview preview(
		HolidayPeriod previous,
		HolidayPeriod current
	) {
		final LocalDate today = today();
		final LocalDate horizonEnd = today.plusMonths(HORIZON_MONTHS);
		final RecurringHolidayImpactCounts previousImpact = previous == null
			? RecurringHolidayImpactCounts.zero()
			: impactRepository.count(previous, today, horizonEnd);
		final RecurringHolidayImpactCounts currentImpact = current == null
			? RecurringHolidayImpactCounts.zero()
			: impactRepository.count(current, today, horizonEnd);
		final RecurringHolidayImpactCounts combinedImpact;
		if (previous == null) {
			combinedImpact = currentImpact;
		}
		else if (current == null) {
			combinedImpact = previousImpact;
		}
		else {
			combinedImpact = impactRepository.countCombined(
				previous,
				current,
				today,
				horizonEnd);
		}
		return new RecurringHolidayImpactPreview(
			previousImpact,
			currentImpact,
			combinedImpact);
	}

	private HolidayPeriod periodOf(RecurringHolidayRule rule) {
		return new HolidayPeriod(
			rule.getDayOfWeek(),
			rule.getEffectiveFrom(),
			maximumDate(rule.getEffectiveTo()));
	}

	private LocalDate maximumDate(LocalDate date) {
		return date == null ? MAXIMUM_DATE : date;
	}

	private RecurringHolidayMutationResult result(
		RecurringHolidayRule rule,
		long pendingVersion,
		RecurringHolidayImpactPreview impact
	) {
		return new RecurringHolidayMutationResult(
			RecurringHolidayRuleView.from(rule),
			pendingVersion,
			impact);
	}

	private void appendAudit(
		RecurringHolidayRule rule,
		String action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason,
		long pendingVersion,
		RecurringHolidayImpactPreview impact
	) {
		auditLogRepository.append(ScheduleAuditLog.create(
			ScheduleAuditTargetType.RECURRING_HOLIDAY,
			"recurring-holiday:" + rule.getId(),
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason,
			Map.of(
				"pendingConfigVersion", pendingVersion,
				"affectedDateCount", impact.combined().affectedDateCount(),
				"templateTimeSlotCount", impact.combined().templateTimeSlotCount(),
				"activeReservationCount", impact.combined().activeReservationCount(),
				"previousImpact", impact.previous(),
				"currentImpact", impact.current())));
	}

	private Map<String, Object> stateOf(RecurringHolidayRule rule) {
		final Map<String, Object> state = new HashMap<>();
		state.put("dayOfWeek", rule.getDayOfWeek().name());
		state.put("effectiveFrom", rule.getEffectiveFrom().toString());
		if (rule.getEffectiveTo() != null) {
			state.put("effectiveTo", rule.getEffectiveTo().toString());
		}
		state.put("reason", rule.getReason());
		state.put("active", rule.isActive());
		return Map.copyOf(state);
	}

	private LocalDate today() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE).toLocalDate();
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
	}
}
