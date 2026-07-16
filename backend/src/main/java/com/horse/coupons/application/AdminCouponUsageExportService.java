package com.horse.coupons.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.infrastructure.AdminCouponUsageExportProjection;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;
import com.horse.global.csv.CsvEncoder;

@Service
public class AdminCouponUsageExportService {

	private static final List<String> HEADERS = List.of(
		"occurred_at",
		"usage_log_id",
		"coupon_id",
		"reservation_id",
		"member_id",
		"member_name",
		"coupon_type",
		"action",
		"count_delta",
		"actor_type",
		"memo");

	private final CouponUsageLogRepository repository;

	public AdminCouponUsageExportService(CouponUsageLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public byte[] export(
		String keyword,
		Long couponId,
		Long reservationId,
		LocalDate occurredDateFrom,
		LocalDate occurredDateTo
	) {
		final AdminCouponUsageExportCriteria criteria = AdminCouponUsageExportCriteria.create(
			keyword,
			couponId,
			reservationId,
			occurredDateFrom,
			occurredDateTo);
		final List<List<String>> rows = repository.findAdminUsageLogsForExport(
			criteria.keyword(),
			criteria.couponId(),
			criteria.reservationId(),
			criteria.occurredAtFrom(),
			criteria.occurredAtTo()).stream()
			.map(AdminCouponUsageExportService::toRow)
			.toList();
		return CsvEncoder.encode(HEADERS, rows);
	}

	private static List<String> toRow(AdminCouponUsageExportProjection usageLog) {
		return List.of(
			usageLog.getOccurredAt().toString(),
			usageLog.getUsageLogId().toString(),
			usageLog.getCouponId().toString(),
			usageLog.getReservationId() == null ? "" : usageLog.getReservationId().toString(),
			usageLog.getMemberId().toString(),
			usageLog.getMemberName(),
			usageLog.getCouponType().value(),
			usageLog.getAction().value(),
			Integer.toString(usageLog.getCountDelta()),
			usageLog.getActorType().value(),
			usageLog.getMemo() == null ? "" : usageLog.getMemo());
	}
}
