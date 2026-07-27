package com.horse.schedules.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.application.AdminReservationQueryService;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ScheduleAuditLogRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ScheduleDateQueryService {

	private static final String CLOSING_STARTED = "CLOSING_STARTED";

	private final ScheduleDateRepository scheduleDateRepository;
	private final ScheduleAuditLogRepository auditLogRepository;
	private final ScheduleDateClosureService closureService;
	private final AdminReservationQueryService reservationQueryService;

	public ScheduleDateQueryService(
		ScheduleDateRepository scheduleDateRepository,
		ScheduleAuditLogRepository auditLogRepository,
		ScheduleDateClosureService closureService,
		AdminReservationQueryService reservationQueryService
	) {
		this.scheduleDateRepository = scheduleDateRepository;
		this.auditLogRepository = auditLogRepository;
		this.closureService = closureService;
		this.reservationQueryService = reservationQueryService;
	}

	@Transactional(readOnly = true)
	public List<ScheduleDateView> findAll(LocalDate dateFrom, LocalDate dateTo) {
		if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_EFFECTIVE_DATE);
		}
		return scheduleDateRepository
			.findAllByScheduleDateBetweenOrderByScheduleDateAsc(dateFrom, dateTo)
			.stream()
			.map(ScheduleDateView::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleDateView find(LocalDate scheduleDate) {
		return scheduleDateRepository.findByScheduleDate(scheduleDate)
			.map(ScheduleDateView::from)
			.orElseThrow(() -> new ScheduleException(
				ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
	}

	@Transactional(readOnly = true)
	public List<ScheduleImpactReservationView> findActiveReservations(LocalDate scheduleDate) {
		return closureService.impact(scheduleDate).reservationIds().stream()
			.map(reservationQueryService::getReservation)
			.map(ScheduleImpactReservationView::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public int findInitialReservationCount(LocalDate scheduleDate, int currentCount) {
		return auditLogRepository.findLatestByTargetAndAction(
				ScheduleAuditTargetType.SCHEDULE_DATE,
				scheduleDate.toString(),
				CLOSING_STARTED)
			.map(ScheduleAuditLog::getMetadata)
			.map(metadata -> metadata.get("initialReservationCount"))
			.filter(Number.class::isInstance)
			.map(Number.class::cast)
			.map(Number::intValue)
			.map(initialCount -> Math.max(initialCount, currentCount))
			.orElse(currentCount);
	}
}
