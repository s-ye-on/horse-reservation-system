package com.horse.coupons.infrastructure;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.horse.coupons.domain.CouponUsageAction;
import com.horse.coupons.domain.CouponUsageLog;

public interface CouponUsageLogRepository extends Repository<CouponUsageLog, Long> {

	CouponUsageLog save(CouponUsageLog usageLog);

	List<CouponUsageLog> findAllByMemberIdOrderByOccurredAtDescIdDesc(Long memberId);

	Optional<CouponUsageLog> findFirstByReservationIdAndActionOrderByIdAsc(
		Long reservationId,
		CouponUsageAction action
	);

	boolean existsByReservationIdAndAction(Long reservationId, CouponUsageAction action);

	@Query("""
		select usageLog.id as usageLogId,
			usageLog.couponId as couponId,
			usageLog.reservationId as reservationId,
			member.id as memberId,
			member.name as memberName,
			coupon.type as couponType,
			usageLog.action as action,
			usageLog.countDelta as countDelta,
			usageLog.occurredAt as occurredAt,
			usageLog.actorType as actorType,
			usageLog.memo as memo
		from CouponUsageLog usageLog, Coupon coupon, Member member
		where coupon.id = usageLog.couponId
		  and member.id = usageLog.memberId
		  and (:couponId is null or usageLog.couponId = :couponId)
		  and (:reservationId is null or usageLog.reservationId = :reservationId)
		  and (
			:keyword is null
			or member.name like concat('%', :keyword, '%')
			or member.phone like concat('%', :keyword, '%')
		  )
		  and (:occurredAtFrom is null or usageLog.occurredAt >= :occurredAtFrom)
		  and (:occurredAtTo is null or usageLog.occurredAt <= :occurredAtTo)
		order by usageLog.occurredAt desc, usageLog.id desc
		""")
	List<AdminCouponUsageExportProjection> findAdminUsageLogsForExport(
		@Param("keyword") String keyword,
		@Param("couponId") Long couponId,
		@Param("reservationId") Long reservationId,
		@Param("occurredAtFrom") LocalDateTime occurredAtFrom,
		@Param("occurredAtTo") LocalDateTime occurredAtTo
	);
}
