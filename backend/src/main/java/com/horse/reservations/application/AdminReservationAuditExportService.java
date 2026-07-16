package com.horse.reservations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.csv.CsvEncoder;
import com.horse.reservations.infrastructure.AdminReservationAuditProjection;
import com.horse.reservations.infrastructure.ReservationChangeLogRepository;

@Service
public class AdminReservationAuditExportService {

	private static final int CRITERIA_PAGE = 0;
	private static final int CRITERIA_SIZE = 100;
	private static final List<String> HEADERS = List.of(
		"occurred_at",
		"audit_log_id",
		"reservation_id",
		"member_id",
		"member_name",
		"actor_type",
		"change_type",
		"from_status",
		"to_status",
		"from_lesson_date",
		"from_start_time",
		"to_lesson_date",
		"to_start_time",
		"coupon_action",
		"memo");

	private final ReservationChangeLogRepository repository;

	public AdminReservationAuditExportService(ReservationChangeLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public byte[] export(
		String keyword,
		Long reservationId,
		LocalDate occurredDateFrom,
		LocalDate occurredDateTo,
		String actorType,
		String changeType
	) {
		final AdminReservationAuditQueryCriteria criteria = AdminReservationAuditQueryCriteria.create(
			keyword,
			reservationId,
			occurredDateFrom,
			occurredDateTo,
			actorType,
			changeType,
			CRITERIA_PAGE,
			CRITERIA_SIZE);
		final Page<AdminReservationAuditProjection> auditLogs = repository.findAdminAuditLogs(
			criteria.keyword(),
			criteria.reservationId(),
			criteria.occurredAtFrom(),
			criteria.occurredAtTo(),
			criteria.actorType(),
			criteria.changeType(),
			Pageable.unpaged());
		final List<List<String>> rows = auditLogs.getContent().stream()
			.map(AdminReservationAuditExportService::toRow)
			.toList();
		return CsvEncoder.encode(HEADERS, rows);
	}

	private static List<String> toRow(AdminReservationAuditProjection auditLog) {
		return List.of(
			value(auditLog.getOccurredAt()),
			value(auditLog.getAuditLogId()),
			value(auditLog.getReservationId()),
			value(auditLog.getMemberId()),
			value(auditLog.getMemberName()),
			auditLog.getActorType() == null ? "" : auditLog.getActorType().databaseValue(),
			auditLog.getChangeType() == null ? "" : auditLog.getChangeType().databaseValue(),
			auditLog.getFromStatus() == null ? "" : auditLog.getFromStatus().databaseValue(),
			auditLog.getToStatus() == null ? "" : auditLog.getToStatus().databaseValue(),
			value(auditLog.getFromLessonDate()),
			value(auditLog.getFromStartTime()),
			value(auditLog.getToLessonDate()),
			value(auditLog.getToStartTime()),
			auditLog.getCouponAction() == null ? "" : auditLog.getCouponAction().databaseValue(),
			value(auditLog.getMemo()));
	}

	private static String value(Object value) {
		if (value instanceof LocalDateTime dateTime) {
			return dateTime.toString();
		}
		if (value instanceof LocalDate date) {
			return date.toString();
		}
		if (value instanceof LocalTime time) {
			return time.toString();
		}
		return value == null ? "" : value.toString();
	}
}
