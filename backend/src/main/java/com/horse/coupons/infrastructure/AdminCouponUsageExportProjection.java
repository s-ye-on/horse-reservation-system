package com.horse.coupons.infrastructure;

import java.time.LocalDateTime;

import com.horse.coupons.domain.CouponActorType;
import com.horse.coupons.domain.CouponType;
import com.horse.coupons.domain.CouponUsageAction;

public interface AdminCouponUsageExportProjection {

	Long getUsageLogId();

	Long getCouponId();

	Long getReservationId();

	Long getMemberId();

	String getMemberName();

	CouponType getCouponType();

	CouponUsageAction getAction();

	int getCountDelta();

	LocalDateTime getOccurredAt();

	CouponActorType getActorType();

	String getMemo();
}
