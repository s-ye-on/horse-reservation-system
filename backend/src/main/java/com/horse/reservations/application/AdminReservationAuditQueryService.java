package com.horse.reservations.application;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.infrastructure.AdminReservationAuditProjection;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;

@Service
public class AdminReservationAuditQueryService {

	private final ReservationChangeLogRepository repository;

	public AdminReservationAuditQueryService(ReservationChangeLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public AdminReservationAuditPageResult getAuditLogs(
		String keyword,
		Long reservationId,
		LocalDate occurredDateFrom,
		LocalDate occurredDateTo,
		String actorType,
		String changeType,
		Integer page,
		Integer size
	) {
		final AdminReservationAuditQueryCriteria criteria = AdminReservationAuditQueryCriteria.create(
			keyword,
			reservationId,
			occurredDateFrom,
			occurredDateTo,
			actorType,
			changeType,
			page,
			size);
		final Page<AdminReservationAuditProjection> auditLogs = repository.findAdminAuditLogs(
			criteria.keyword(),
			criteria.reservationId(),
			criteria.occurredAtFrom(),
			criteria.occurredAtTo(),
			criteria.actorType(),
			criteria.changeType(),
			PageRequest.of(criteria.page(), criteria.size()));
		return new AdminReservationAuditPageResult(
			auditLogs.getContent().stream().map(AdminReservationAuditResult::from).toList(),
			auditLogs.getNumber(),
			auditLogs.getSize(),
			auditLogs.getTotalElements(),
			auditLogs.getTotalPages(),
			auditLogs.hasNext());
	}
}
